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

## API 및 라우터 설정 (상세)

### 기본 계정 및 비밀번호
시스템에서 사용하는 기본 키 값입니다. 운영 환경에서는 환경 변수를 통해 변경하는 것을 권장합니다.

- **Admin Key (관리자 키):** `admin`
  - **설정 변수:** `ADMIN_KEY`
  - **용도:** 관리자 페이지 접속 및 관리자 API 호출 인증.
- **Plugin Secret (플러그인 통신 키):** `secret`
  - **설정 변수:** `PLUGIN_SECRET`
  - **용도:** 마인크래프트 서버(플러그인)와 웹 백엔드 간의 통신 인증.

### 주요 라우터 (엔드포인트)

#### 1. 관리자 페이지 및 API (`/admin`)
- **웹 접근:** `http://localhost:3000/admin`
  - 브라우저를 통해 접속하며, 물품 가격 조정 및 상점 관리를 수행합니다.
  - 접속 시 `Admin Key` 입력을 요구합니다.
- **API 엔드포인트:** `/api/admin/*`
  - 인증 방식: HTTP 헤더 `x-admin-key` 또는 쿼리 파라미터 `?key=`에 키 값을 포함해야 합니다.

#### 2. 상점 접속 페이지 (`/create`)
- **웹 접근:** `http://localhost:3000/create`
- **기능:** 플레이어가 닉네임을 입력하여 상점에 로그인하는 페이지입니다.
- **동작 원리:** 닉네임 입력 시 내부적으로 `/create/shop/:playerId` API를 호출하여 생성된 상점 URL로 이동합니다.

#### 3. 상점 URL 생성 API (`/create/shop/:playerId`)
- **경로:** `/create/shop/:playerId` (GET)
- **기능:** 특정 플레이어의 상점 접속을 위한 고유(인코딩된) URL을 생성합니다. (백엔드 API)
- **예시:** `http://localhost:3000/create/shop/Steve`
- **응답 (JSON):**
  ```json
  {
    "playerName": "Steve",
    "encoded": "U3RldmU",
    "url": "http://localhost:3000/shop/U3RldmU"
  }
  ```

### 3. 사용법
- **플레이어:** `http://localhost:3000/create`에 접속하여 마인크래프트 이름을 입력하세요.
- **상점:** 아이템을 둘러보세요. "즉시 구매"는 즉시 차감됩니다. "외상"은 5일(마인크래프트 시간) 내에 상환해야 합니다.
- **관리자:** `http://localhost:3000/admin`에 접속하세요. 기본 키는 `admin`입니다.
- **상환:** 게임 내에서 화폐 아이템을 획득하세요. 부채 상환을 위해 자동으로 차감됩니다.
- **패널티:** 부채 상환 기한이 연체(OVERDUE)되면, 사망 시 (KeepInventory를 무시하고) 모든 인벤토리 아이템을 잃게 됩니다.
- **TP 티켓:** TP 티켓을 구매하고 손에 든 상태에서 우클릭하면 플레이어 선택기가 열립니다.

## 문제 해결
- **프론트엔드가 로드되지 않나요?** `Web/front`에서 `npm run build`를 실행했는지 확인하세요.
- **플러그인 오류가 발생하나요?** 콘솔을 확인하세요. 백엔드가 실행 중이고 접근 가능한지 확인하세요.
- **시간이 흐르지 않나요?** `currentTick`은 서버가 실행 중일 때만 진행됩니다.
