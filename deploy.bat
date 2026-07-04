@echo off
REM 탐색기에서 더블클릭으로 deploy.sh 를 실행하기 위한 래퍼.
REM 배포하려는 변경사항은 반드시 먼저 git commit 되어 있어야 한다(deploy.sh 참고).
cd /d "%~dp0"
"C:\Program Files\Git\usr\bin\bash.exe" -lc "./deploy.sh"
echo.
echo ===== 배포 스크립트 종료. 위 로그에서 에러 여부를 확인하세요. =====
pause
