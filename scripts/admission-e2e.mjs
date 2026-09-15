// Local-only integration harness. Java 21+, Node 20+, Docker and built sibling service jars are required.
// All generated data, keys and logs stay outside Git. PG calls terminate at this loopback mock.
import { spawn, spawnSync } from 'node:child_process';
import { generateKeyPairSync, sign } from 'node:crypto';
import { createServer } from 'node:http';
import { createServer as createPortProbe } from 'node:net';
import { mkdirSync, readFileSync, writeFileSync, openSync, readdirSync, copyFileSync, unlinkSync } from 'node:fs';
import { dirname, resolve, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const runId = 'admission-trusted-e2e-' + new Date().toISOString().replace(/[-:.TZ]/g, '');
const output = resolve(root, '../analytics/evidence/msa4-lms-v2', runId);
mkdirSync(output, { recursive: true });
const prefix = 'lms-' + runId;
const label = 'lms.test=' + runId;
const containers = [];
const runtimeCopies = [];
const processes = new Map();
// Windows javapath can launch a second java.exe; own the actual JVM for reliable stop/restart.
const javaSettings = spawnSync('java', ['-XshowSettings:properties', '-version'], { encoding: 'utf8', windowsHide: true });
const javaHome = /java\.home\s*=\s*(.+)/.exec(javaSettings.stderr)?.[1]?.trim();
assert(javaHome, 'Cannot resolve actual Java runtime');
const java = join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
const orders = new Map();
const state = { phase: 'starting', checks: [], error: null };
const ports = { scg: 18080, auth: 18081, academic: 18082, payment: 18083 };
const repo = name => join(root, 'msa4-lms-v2-' + name);
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
function docker(args, input) {
  const result = spawnSync('docker', args, { input, encoding: 'utf8', windowsHide: true, maxBuffer: 12 * 1024 * 1024 });
  if (result.status !== 0) throw new Error(`Docker ${args[0]} failed: ${result.stderr}`);
  return result.stdout.trim();
}
function sql(database, query) {
  return docker(['exec', '-i', prefix + '-mysql', 'mysql', '-uroot', '-pe2e-local-only', '-N', '-B', '--default-character-set=utf8mb4', database], query);
}
function container(name, image, args = [], command = []) {
  const fullName = prefix + '-' + name;
  docker(['run', '-d', '--name', fullName, '--label', label, ...args, image, ...command]);
  containers.push(fullName);
}
async function waitUntil(check, message, timeout = 90000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    try { if (await check()) return; } catch { /* Startup/recovery can be temporarily unavailable. */ }
    await sleep(1000);
  }
  throw new Error(message);
}
const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
function token() {
  const input = encode({ alg: 'RS256', typ: 'JWT', kid: 'e2e' }) + '.' + encode({
    sub: '900001', iss: 'meerkat@meerkat.kr', aud: ['lms-api'], role: 'ADMIN', token_type: 'access',
    iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600,
  });
  return input + '.' + sign('RSA-SHA256', Buffer.from(input), privateKey).toString('base64url');
}
async function api(path, body, method = body === undefined ? 'GET' : 'POST') {
  const response = await fetch('http://127.0.0.1:18080' + path, {
    method, headers: { Authorization: 'Bearer ' + token(), 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15000),
  });
  const raw = await response.text();
  if (!response.ok) throw new Error(`${method} ${path}: ${response.status} ${raw.slice(0, 900)}`);
  return JSON.parse(raw).data;
}
const commonEnv = {
  ...process.env, SPRING_PROFILES_ACTIVE: 'admission-e2e', DB_HOST: '127.0.0.1', DB_PORT: '13360',
  DB_USER: 'root', DB_USERNAME: 'root', DB_PASSWORD: 'e2e-local-only', REDIS_HOST: '127.0.0.1', REDIS_PORT: '16379', REDIS_PASSWORD: '',
  KAFKA_BOOTSTRAP_SERVERS: '127.0.0.1:19092', MINIO_ENDPOINT: 'http://127.0.0.1:19000',
  MINIO_ACCESS_KEY: 'e2e-user', MINIO_SECRET_KEY: 'e2e-local-secret', MINIO_BUCKET: 'e2e-files',
  JWT_KID: 'e2e', JWT_PRIVATE_KEY_B64: Buffer.from(privateKey.export({ type: 'pkcs8', format: 'pem' })).toString('base64'),
  JWT_PUBLIC_KEY_B64: Buffer.from(publicKey.export({ type: 'spki', format: 'pem' })).toString('base64'),
  GATEWAY_URI: 'http://127.0.0.1:18080', APP_DESCRIPTION: 'local-e2e', FILE_SERVER_URI: 'http://127.0.0.1:18081', FILE_STORAGE_PATH: output,
  ADMISSION_ACADEMIC_BASE_URL: 'http://127.0.0.1:18082', ACADEMIC_INTERNAL_BASE_URL: 'http://127.0.0.1:18082',
  GATEWAY_INTERNAL_BASE_URL: 'http://127.0.0.1:18082', AUTH_SERVICE_URL: 'http://127.0.0.1:18081',
  TOSS_SECRET_KEY: 'e2e-not-a-real-pg-key', TOSS_CLIENT_KEY: 'e2e-not-a-real-client-key', CERTIFICATE_SIGNING_KEY: 'e2e-local-signing-key-long-enough',
  CORS_ALLOW_ORIGIN: 'http://127.0.0.1:15178', AUTH_SERVICE_NAME: 'auth', AUTH_SERVICE_URI: 'http://127.0.0.1:18081', AUTH_SERVICE_PREDICATE: '/api/auth/**', AUTH_SERVICE_OPEN_API_PATH: '/api-docs',
  ACADEMIC_SERVICE_NAME: 'academic', ACADEMIC_SERVICE_URI: 'http://127.0.0.1:18082', ACADEMIC_SERVICE_PREDICATE: '/api/academic/**', ACADEMIC_SERVICE_OPEN_API_PATH: '/api-docs',
  PAYMENT_SERVICE_NAME: 'payment', PAYMENT_SERVICE_URI: 'http://127.0.0.1:18083', PAYMENT_SERVICE_PREDICATE: '/api/payment/**', PAYMENT_SERVICE_OPEN_API_PATH: '/api-docs',
};
// 부모 셸에 이전 입학 토큰이 있어도 이번 검증에서는 전달하지 않는다.
delete commonEnv.ADMISSION_PAYMENT_TOKEN;
delete commonEnv.ADMISSION_AUTH_TOKEN;
function startService(name) {
  if (processes.has(name)) throw new Error(name + ' is already running');
  const jar = readdirSync(join(repo(name), 'build/libs')).find(file => file.endsWith('.jar') && !file.endsWith('-plain.jar'));
  assert(jar, name + ' bootJar missing');
  const runtimeJar = join(output, name + '-' + Date.now() + '.jar');
  copyFileSync(join(repo(name), 'build/libs', jar), runtimeJar);
  runtimeCopies.push(runtimeJar);
  const fd = openSync(join(output, name + '.log'), 'a');
  const child = spawn(java, ['-Xmx512m', '-jar', runtimeJar,
    '--server.port=' + ports[name], '--spring.sql.init.mode=never', '--spring.jpa.show-sql=false',
    '--logging.level.root=INFO', '--logging.file.name=' + join(output, name + '-app.log'),
    '--toss.payments.base-url=http://127.0.0.1:18089', '--spring.kafka.listener.auto-startup=true'], {
    cwd: output, env: { ...commonEnv, APP_PORT: String(ports[name]), DB_NAME: 'lms_' + name }, windowsHide: true,
    stdio: ['ignore', fd, fd],
  });
  processes.set(name, child);
  child.on('exit', () => { if (processes.get(name) === child) processes.delete(name); });
}
async function stopService(name) {
  const child = processes.get(name);
  if (!child) return;
  const done = new Promise(resolve => child.once('exit', resolve));
  child.kill();
  await done;
}
async function seed() {
  sql('mysql', 'CREATE DATABASE lms_academic; CREATE DATABASE lms_auth; CREATE DATABASE lms_payment;');
  sql('lms_academic', readFileSync(join(repo('academic'), 'src/main/resources/schema.sql'), 'utf8'));
  sql('lms_payment', readFileSync(join(repo('payment'), 'src/main/resources/schema.sql'), 'utf8'));
  // Auth has no tracked baseline DDL. This minimal fixture follows Account's current mapping.
  sql('lms_auth', `CREATE TABLE accounts(id BIGINT PRIMARY KEY AUTO_INCREMENT, admission_candidate_id BIGINT UNIQUE,
    login_id VARCHAR(150) NOT NULL UNIQUE,password VARCHAR(255) NOT NULL,birth_date DATE NULL,role VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,failed_login_attempts INT NOT NULL DEFAULT 0,locked_until DATETIME NULL,
    requires_password_change BOOLEAN NOT NULL DEFAULT FALSE,created_at DATETIME NOT NULL,updated_at DATETIME NOT NULL,deleted_at DATETIME NULL);
    CREATE TABLE audit_logs(id BIGINT PRIMARY KEY AUTO_INCREMENT,actor_id BIGINT NOT NULL,action VARCHAR(100) NOT NULL,
    target_type VARCHAR(100) NOT NULL,target_id BIGINT NOT NULL,before_value JSON,after_value JSON,reason VARCHAR(500),
    request_id VARCHAR(100),ip_address VARCHAR(45),created_at DATETIME NOT NULL);`);
  sql('lms_auth', readFileSync(join(repo('auth'), 'src/main/resources/migration/20260907_create_account_sync_outbox.sql'), 'utf8'));
  sql('lms_auth', readFileSync(join(repo('auth'), 'src/main/resources/migration/20260915_admission_account_compatibility.sql'), 'utf8'));
  const year = new Date().getFullYear() + 1;
  sql('lms_academic', `INSERT INTO users(id,name,email,role,status) VALUES(900001,'E2E 관리자','e2e-admin@example.invalid','ADMIN','ACTIVE'),(900002,'E2E 교수','e2e-prof@example.invalid','PROFESSOR','ACTIVE');
    INSERT INTO colleges(id,code,name) VALUES(1,'E2E','E2E 대학');
    INSERT INTO departments(id,code,college_id,name) VALUES(1,'001',1,'E2E 학과');
    INSERT INTO professors(id,user_id,department_id,hire_year,professor_number) VALUES(1,900002,1,2020,'p-e2e');
    INSERT INTO semesters(id,academic_year,term,start_date,end_date,enrollment_start_at,enrollment_end_at,is_current)
    VALUES(1,${year},'FIRST','${year}-03-01','${year}-06-30','${year}-02-01','${year}-02-28',1);`);
  sql('lms_payment', `INSERT INTO semester_snapshots VALUES(1,'E2E 1학기','${year}-03-01','${year}-06-30',1,NOW());`);
}
function checkpoint(name, data = {}) {
  state.checks.push({ name, ...data });
  writeFileSync(join(output, 'result.json'), JSON.stringify(state, null, 2));
  console.log('PASS ' + name);
}
async function scenario() {
  state.phase = 'scenario'; state.error = null;
  const year = new Date().getFullYear() + 1;
  const unique = String(Date.now());
  const candidate = await api('/api/academic/admission-candidates', {
    name: 'E2E 입학', birthDate: '2008-03-15', email: `e2e-${unique}@example.invalid`,
    departmentId: 1, advisorProfessorId: 1, admissionYear: year,
  });
  const id = candidate.id; state.candidateId = id;
  const internalPath = '/api/academic/internal/admissions/' + id;
  const internal = await fetch('http://127.0.0.1:18082' + internalPath);
  assert.equal(internal.status, 200);
  assert.equal((await internal.json()).data.tuitionPaid, false);
  for (const [method, suffix] of [['GET', ''], ['POST', '/bill'], ['POST', '/paid'], ['POST', '/student'], ['POST', '/activated']]) {
    const external = await fetch('http://127.0.0.1:18080' + internalPath + suffix, {
      method, headers: { Authorization: 'Bearer ' + token(), 'Content-Type': 'application/json' },
      body: method === 'POST' ? '{}' : undefined,
    });
    assert.equal(external.status, 401);
  }
  checkpoint('입학 토큰 없이 내부 조회 성공, 관리자 JWT도 SCG 내부 경로 접근 차단');
  assert.equal(candidate.status, 'PENDING');
  assert.equal(sql('lms_auth', `SELECT COUNT(*) FROM accounts WHERE admission_candidate_id=${id}`), '0');
  checkpoint('등록만으로 계정을 만들지 않음', { candidateId: id });
  const dueDate = new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10);
  const input = { semesterId: 1, billingAmount: 1000000, dueDate, bankCode: '88' };
  const detail = await api(`/api/payment/admission-candidates/${id}/tuition`, input);
  const billId = detail.bill.id;
  const order = orders.get('ADMISSION-' + billId);
  assert(order, 'PG issue must use deterministic order');
  // 헤더 인증 제거가 실제 완납 검증을 우회하지 않는지 worker의 한 주기 이상 확인한다.
  await sleep(12000);
  assert.equal(sql('lms_academic', `SELECT tuition_paid FROM admission_candidates WHERE id=${id}`), '0');
  assert.equal(sql('lms_auth', `SELECT COUNT(*) FROM accounts WHERE admission_candidate_id=${id}`), '0');
  checkpoint('PG 미입금 상태에서는 완납 통지와 계정 생성 없음');
  const again = await api(`/api/payment/admission-candidates/${id}/tuition`, input);
  assert.equal(again.virtualAccount.id, detail.virtualAccount.id);
  checkpoint('Gateway 발급 및 중복 요청의 동일 계좌 반환', { billId });
  await stopService('auth');
  order.status = 'DONE'; order.lastTransactionKey = 'deposit-' + order.orderId;
  const webhook = { secret: order.secret, status: 'DONE', transactionKey: order.lastTransactionKey,
    orderId: order.orderId, createdAt: new Date().toISOString() };
  const deposit = () => fetch('http://127.0.0.1:18080/api/payment/webhooks/toss/virtual-account-deposits', {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'tosspayments-webhook-transmission-id': 'event-' + unique,
      'tosspayments-webhook-transmission-time': new Date().toISOString() }, body: JSON.stringify(webhook),
  });
  assert.equal((await deposit()).status, 200);
  assert.equal((await deposit()).status, 200);
  assert.equal(sql('lms_payment', `SELECT COUNT(*) FROM payments WHERE tuition_bill_id=${billId}`), '1');
  assert.equal(sql('lms_auth', `SELECT COUNT(*) FROM accounts WHERE admission_candidate_id=${id}`), '0');
  checkpoint('중복 웹훅 1건 저장 및 재확인 대기', { billId });
  await waitUntil(() => sql('lms_academic', `SELECT tuition_paid FROM admission_candidates WHERE id=${id}`) === '1',
    'Payment -> Academic full-payment notification failed', 250000);
  checkpoint('실제 대기 시간 이후 완납 통지, Auth 중단 중 요청 보존');
  startService('auth');
  await waitUntil(async () => {
    const status = await api('/api/academic/admission-candidates/' + id);
    return status.status === 'COMPLETED';
  }, 'Auth restart did not complete admission', 90000);
  await waitUntil(() => sql('lms_payment', `SELECT admission_sync_complete FROM tuition_bills WHERE id=${billId}`) === '1', 'Student link not completed', 60000);
  assert.equal(sql('lms_auth', `SELECT COUNT(*) FROM accounts WHERE admission_candidate_id=${id} AND status='ACTIVE'`), '1');
  assert.equal(sql('lms_academic', `SELECT COUNT(*) FROM admission_candidates c JOIN students s ON s.id=c.student_id WHERE c.id=${id}`), '1');
  checkpoint('Auth 재기동 후 계정·학생 1건, COMPLETED 및 Payment 학생 연결');
  const second = await api('/api/academic/admission-candidates', {
    name: 'E2E 재발급', birthDate: '2008-03-15', email: `reissue-${unique}@example.invalid`,
    departmentId: 1, advisorProfessorId: 1, admissionYear: year,
  });
  const original = await api(`/api/payment/admission-candidates/${second.id}/tuition`, input);
  const oldOrder = orders.get('ADMISSION-' + original.bill.id);
  // Only this generated fixture is expired; no shared database or real PG is used.
  sql('lms_payment', `UPDATE virtual_accounts SET expires_at=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE id=${original.virtualAccount.id}`);
  oldOrder.status = 'EXPIRED';
  const reissueInput = { previousVirtualAccountId: original.virtualAccount.id, dueDate };
  const renewed = await api(`/api/payment/admission-candidates/${second.id}/tuition/reissue`, reissueInput);
  const replay = await api(`/api/payment/admission-candidates/${second.id}/tuition/reissue`, reissueInput);
  assert.equal(renewed.virtualAccount.id, replay.virtualAccount.id);
  assert.notEqual(renewed.virtualAccount.id, original.virtualAccount.id);
  oldOrder.status = 'DONE'; oldOrder.lastTransactionKey = 'late-' + unique;
  const late = await fetch('http://127.0.0.1:18080/api/payment/webhooks/toss/virtual-account-deposits', {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'tosspayments-webhook-transmission-id': 'late-' + unique,
      'tosspayments-webhook-transmission-time': new Date().toISOString() },
    body: JSON.stringify({ secret: oldOrder.secret, status: 'DONE', transactionKey: oldOrder.lastTransactionKey,
      orderId: oldOrder.orderId, createdAt: new Date().toISOString() }),
  });
  assert.equal(late.status, 200);
  assert.equal(sql('lms_payment', `SELECT COUNT(*) FROM refunds WHERE virtual_account_id=${original.virtualAccount.id}`), '1');
  assert.equal(sql('lms_payment', `SELECT COUNT(*) FROM payments WHERE tuition_bill_id=${original.bill.id}`), '0');
  checkpoint('만료 재발급 반복 요청 1건 및 이전 계좌 늦은 입금의 환불 분리');
  const newOrder = orders.get(`ADMISSION-${original.bill.id}-R${original.virtualAccount.id}`);
  newOrder.status = 'DONE'; newOrder.lastTransactionKey = 'missed-' + unique;
  // Deliberately omit the new deposit webhook and restart Payment with the same DB.
  await stopService('payment'); startService('payment');
  await waitUntil(() => sql('lms_academic', `SELECT status FROM admission_candidates WHERE id=${second.id}`) === 'COMPLETED',
    'Missing webhook recovery on reissued account failed', 330000);
  await waitUntil(() => sql('lms_payment', `SELECT admission_sync_complete FROM tuition_bills WHERE id=${original.bill.id}`) === '1', 'Reissued student link incomplete', 60000);
  assert.equal(sql('lms_auth', `SELECT COUNT(*) FROM accounts WHERE admission_candidate_id=${second.id}`), '1');
  assert.equal(sql('lms_payment', `SELECT COUNT(*) FROM payments WHERE tuition_bill_id=${original.bill.id}`), '1');
  checkpoint('Payment 재기동·새 계좌 웹훅 유실 후 PG 조회 복구와 학생 자동 생성');
  state.phase = 'passed';
  writeFileSync(join(output, 'result.json'), JSON.stringify(state, null, 2));
}
async function cleanup() {
  for (const name of [...processes.keys()]) await stopService(name);
  for (const name of containers.reverse()) {
    if (docker(['inspect', '--format', '{{index .Config.Labels "lms.test"}}', name]) !== runId) throw new Error('Container ownership mismatch');
    docker(['rm', '-f', name]);
  }
  for (const path of runtimeCopies) unlinkSync(path);
}
const server = createServer(async (request, response) => {
  const chunks = []; for await (const chunk of request) chunks.push(chunk);
  const body = chunks.length ? JSON.parse(Buffer.concat(chunks).toString()) : {};
  let result; let status = 200;
  try {
    if (request.url === '/control/status') result = state;
    else if (request.url === '/control/run' && request.method === 'POST') {
      if (!['ready', 'failed', 'passed'].includes(state.phase)) { status = 409; result = { error: 'busy' }; }
      else { scenario().catch(error => { state.phase = 'failed'; state.error = error.message; console.error(error.message); writeFileSync(join(output, 'result.json'), JSON.stringify(state, null, 2)); }); result = { started: true }; }
    } else if (request.url === '/control/stop' && request.method === 'POST') {
      result = { stopping: true }; setTimeout(async () => { await cleanup(); server.close(); }, 100);
    } else if (request.url === '/v1/virtual-accounts' && request.method === 'POST') {
      result = orders.get(body.orderId);
      if (!result) {
        result = { orderId: body.orderId, paymentKey: 'pk-' + body.orderId, secret: 'secret-' + body.orderId,
          status: 'WAITING_FOR_DEPOSIT', totalAmount: body.amount, balanceAmount: body.amount,
          virtualAccount: { accountNumber: 'E2E-' + (orders.size + 1), bankCode: body.bank, dueDate: body.dueDate } };
        orders.set(body.orderId, result);
      }
    } else if (request.url.startsWith('/v1/payments/orders/')) {
      result = orders.get(decodeURIComponent(request.url.split('/').at(-1)));
      if (!result) { status = 404; result = { code: 'NOT_FOUND_PAYMENT', message: 'mock order not found' }; }
    } else if (/^\/v1\/payments\/.+\/cancel$/.test(request.url)) {
      result = [...orders.values()].find(order => order.paymentKey === decodeURIComponent(request.url.split('/')[3]));
      if (result) { result.status = 'CANCELED'; result.balanceAmount = 0; }
      else status = 404;
    } else { status = 404; result = { error: 'Unsupported local mock route' }; }
  } catch (error) { status = 500; result = { error: error.message }; }
  response.writeHead(status, { 'Content-Type': 'application/json' }); response.end(JSON.stringify(result));
});
await new Promise(resolve => server.listen(18089, '127.0.0.1', resolve));
try {
  for (const port of [...Object.values(ports), 13360, 16379, 19092, 19000]) {
    await new Promise((resolve, reject) => {
      const probe = createPortProbe();
      probe.once('error', () => reject(new Error(`Local test port ${port} is occupied; no service was stopped`)));
      probe.listen(port, '127.0.0.1', () => probe.close(resolve));
    });
  }
  container('mysql', 'mysql:8.4', ['-p', '127.0.0.1:13360:3306', '-e', 'MYSQL_ROOT_PASSWORD=e2e-local-only']);
  container('redis', 'redis:7.4-alpine', ['-p', '127.0.0.1:16379:6379']);
  container('kafka', 'apache/kafka:3.9.1', ['-p', '127.0.0.1:19092:9092', '-e', 'KAFKA_NODE_ID=1', '-e', 'KAFKA_PROCESS_ROLES=broker,controller',
    '-e', 'KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093', '-e', 'KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://127.0.0.1:19092',
    '-e', 'KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER', '-e', 'KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT',
    '-e', 'KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093', '-e', 'KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1',
    '-e', 'KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1', '-e', 'KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1']);
  container('minio', 'quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z', ['-p', '127.0.0.1:19000:9000', '-e', 'MINIO_ROOT_USER=e2e-user', '-e', 'MINIO_ROOT_PASSWORD=e2e-local-secret'], ['server', '/data']);
  await waitUntil(() => sql('mysql', 'SELECT 1') === '1', 'MySQL not ready');
  await seed();
  for (const name of ['academic', 'auth', 'payment', 'scg']) startService(name);
  for (const name of Object.keys(ports)) await waitUntil(async () => {
    const response = await fetch(`http://127.0.0.1:${ports[name]}/actuator/health/liveness`); return response.status < 500;
  }, name + ' did not start; inspect its local log');
  state.phase = 'ready'; console.log('READY http://127.0.0.1:18089/control/status');
} catch (error) {
  state.phase = 'failed'; state.error = error.message; console.error(error.message);
}
