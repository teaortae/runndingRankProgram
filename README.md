# Runner Sheet Input

Kotlin + Compose Desktop로 만든 러닝 기록 입력기입니다.

기능:

- 왼쪽 참가자 목록에서 선택, 추가, 삭제
- 선택한 참가자의 이름, 성별, 목표 거리, 누적 거리 수정
- 사진 업로드 후 OCR로 이름/거리 자동 인식
- Google Sheets 링크 저장 및 브라우저에서 열기
- 전체 표를 Google Sheets에 직접 동기화
- GitHub Actions로 Windows `.exe` 자동 빌드

주요 흐름:

1. `Google Sheets 링크`에 대상 시트 주소를 저장합니다.
2. 왼쪽에서 참가자를 고르거나 `새 참가자 추가`로 목록을 늘립니다.
3. 오른쪽에서 사진 업로드로 오늘 기록을 반영하거나, 참가자 기본 정보를 수정합니다.

저장 위치:

- 앱 상태 파일: `~/.runner-sheet-input/state.json`
- 지원 스크립트: `~/.runner-sheet-input/support/`

실행:

```bash
./gradlew run
```

패키징:

```bash
./gradlew packageDmg
```

Windows `.exe` 설치 프로그램:

- 이 프로젝트는 `packageExe`와 `packageMsi`를 지원하도록 설정되어 있습니다.
- 실제 `.exe` 생성은 Windows에서 실행해야 합니다.
- Windows에서는 `scripts\build-windows-installer.bat` 또는 아래 명령을 사용하면 됩니다.
- GitHub에 push하면 `.github/workflows/windows-exe.yml`이 `main` 브랜치 기준으로 Windows `.exe` artifact를 자동 생성합니다.

```bat
gradlew.bat packageExe
```

- 결과물 위치: `build\compose\binaries\main-exe\`

필수 환경:

- JDK 17 이상
- macOS
- Google Chrome 설치
- Chrome에서 대상 시트에 로그인/편집 가능한 상태
- Python 패키지:

```bash
python3 -m pip install --user selenium browser-cookie3
```

사진 OCR은 macOS Vision을 사용합니다. 기본 데이터는 현재 사용 중인 시트 내용을 기준으로 들어 있습니다.

참고:

- Windows 빌드에서도 앱은 실행되지만, 사진 OCR은 현재 macOS에서만 지원합니다.
- Windows Actions artifact는 설치 파일과 `main-exe` 출력물을 함께 업로드합니다.
