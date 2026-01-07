# TR:ADE - Token Relay: Account-Driven Exchange

Minecraft Paper 1.21.11을 위한 종합 상점 시스템으로, React/Node.js 웹 인터페이스와 통합 플러그인을 제공합니다.

## 폴더 구조
- `Web/`: Node.js 백엔드 및 React 프론트엔드.
  - `server.js`: Express 백엔드.
  - `front/`: React 프론트엔드 (Vite).
  - `data/`: SQLite 데이터베이스 저장소.
- `/`: (루트 디렉토리 소스) 마인크래프트 플러그인 로직 (Java).

## 필수 조건
- Node.js (LTS)
- Java 21 (JDK)
- Minecraft Paper 1.21.11 서버

## 설치 및 실행

### 1. 웹 시스템 (백엔드 + 프론트엔드)

**프론트엔드 빌드:**
```bash
cd Web/front
npm install
npm run build
```

**백엔드 시작:**
```bash
cd Web
npm install
# 필요한 경우 환경 변수 설정 (기본값 제공됨)
node server.js
```
웹 서버는 기본적으로 `http://localhost:3000`에서 실행됩니다.

### 2. 마인크래프트 플러그인

**빌드:**
```bash
# 루트 디렉토리 (build.gradle이 있는 곳)
./gradlew build
```
빌드된 JAR 파일은 `build/libs` (또는 설정된 경로)에 생성됩니다. 서버의 `plugins/` 폴더로 복사하세요.

**설정:**
최초 실행 시 `plugins/TRADE/config.yml` 파일이 생성됩니다.
- `backendBaseUrl`: 웹 서버 URL로 설정하세요 (예: `http://localhost:3000`).
- `pluginSecret`: `Web/server.js`의 `PLUGIN_SECRET`과 일치해야 합니다 (기본값: `secret`).

### 3. 사용법
- **플레이어:** `http://localhost:3000`에 접속하여 마인크래프트 이름을 입력하세요.
- **상점:** 아이템을 둘러보세요. "즉시 구매"는 즉시 차감됩니다. "외상"은 5일(마인크래프트 시간) 내에 상환해야 합니다.
- **관리자:** `http://localhost:3000/admin`에 접속하세요. 기본 키는 `admin`입니다.
- **상환:** 게임 내에서 화폐 아이템을 획득하세요. 부채 상환을 위해 자동으로 차감됩니다.
- **패널티:** 부채 상환 기한이 연체(OVERDUE)되면, 사망 시 (KeepInventory를 무시하고) 모든 인벤토리 아이템을 잃게 됩니다.
- **TP 티켓:** TP 티켓을 구매하고 손에 든 상태에서 우클릭하면 플레이어 선택기가 열립니다.

## 문제 해결
- **프론트엔드가 로드되지 않나요?** `Web/front`에서 `npm run build`를 실행했는지 확인하세요.
- **플러그인 오류가 발생하나요?** 콘솔을 확인하세요. 백엔드가 실행 중이고 접근 가능한지 확인하세요.
- **시간이 흐르지 않나요?** `currentTick`은 서버가 실행 중일 때만 진행됩니다.