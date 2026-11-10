# 기여 안내

## 가장 도움이 되는 것: 기종별 동작 제보
1. 앱 홈 화면 맨 아래 [문제 신고용 진단 정보 보내기]로 텍스트를 만들고
2. [Bug report](../../issues/new?template=bug_report.yml)에 붙여 넣고, 어떤 화면에서 멈췄는지 적어 주세요.
3. 가능하면 스크린샷 한 장(멈춘 화면)을 첨부해 주세요. 개인 정보가 보이면 가리고 올려 주세요.

## 코드 기여
- 빌드/테스트: `cd android && ./gradlew testDebugUnitTest assembleOpen`
- 엔진(`engine/CacheCleanService.kt`)은 실기기에서 검증된 타이밍 값이 많습니다. 바꿀 때는 어떤 기기에서 확인했는지 PR에 적어 주세요.
- 글자 하드코딩 금지: [캐시 삭제] 버튼은 설정 앱의 문자열 리소스(`clear_cache_btn_text`)로 찾습니다.
- 좌표 탭 같은 우회는 받지 않습니다(옆 버튼이 "데이터 삭제"일 수 있음).
