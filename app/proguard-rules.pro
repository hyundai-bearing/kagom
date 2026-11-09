# Pleos Vehicle SDK: 바인더·콜백을 리플렉션으로 사용할 수 있어 유지
-keep class ai.pleos.** { *; }
-dontwarn ai.pleos.**

# Android 차량 API는 기기 시스템 라이브러리
-dontwarn android.car.**

# Pleos SDK 내부에서 Gson으로 응답을 변환한다. 제네릭·어노테이션 정보를 지우면
# 지원 여부(CapabilityConfig) 등이 기본값으로 읽혀 모든 항목이 "확인 불가"가 된다.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class ai.pleos.playground.** { *; }
