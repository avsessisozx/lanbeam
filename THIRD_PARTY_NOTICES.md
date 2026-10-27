# Üçüncü taraf bileşenler / Third-party notices

LanBeam kendi kodu MIT lisanslıdır (bkz. [LICENSE](LICENSE)). Aşağıdaki açık kaynak bileşenler kullanılır.

## Android uygulaması

| Bileşen | Kullanım | Lisans |
|---|---|---|
| [ZXing ("Zebra Crossing")](https://github.com/zxing/zxing) | QR kodu üretme ve çözme | Apache License 2.0 |
| [zxing-android-embedded](https://github.com/journeyapps/zxing-android-embedded) (Journey Mobile) | Kamerayla QR okuma ekranı | Apache License 2.0 |

Apache License 2.0 metni: <https://www.apache.org/licenses/LICENSE-2.0>

Bu bileşenler değiştirilmeden, derleme sırasında Maven deposundan alınır; kaynak kodları bu depoda yoktur.

## Derleme araçları (uygulamaya dahil edilmez)

- Gradle Wrapper (`android/gradlew`, `android/gradle/wrapper/`): Apache License 2.0, Gradle Inc.
- Android Gradle Plugin ve Android SDK: Google, Apache License 2.0 / Android SDK lisansı.

## Yazı tipleri ve görseller

Uygulamayla birlikte dağıtılan yazı tipi, resim veya ses dosyası yoktur. Simgeler bu projede çizilmiş vektörlerdir.
Windows arayüzü yazı tipi olarak sistemdeki Segoe UI'yi kullanır (dağıtılmaz).

## İmza anahtarı

`android/keystore/lanbeam-shared.jks` herkese açık, paylaşılan bir anahtardır; yalnızca kişisel kullanım içindir.
Bu anahtarla imzalanmış bir APK'nın LanBeam'in resmi sürümü olduğu anlamına gelmez; Play Store'a yüklemek için kullanmayın.
