# LanBeam — durum (27 Ekim 2026, v0.2.0 hazırlığı)

## Yapıldı
- Android ve Windows arayüzü yenilendi (kart düzeni, açık/koyu tema; Windows'ta Ayarlar'dan tema seçimi).
- İmza: depodaki sabit anahtar (android/keystore/lanbeam-shared.jks) ile; secret/yerel dosya gerekmez.
- Özgünlük taraması: başka uygulamadan kopya kod/isim bulunmadı (kendi protokolü, MIT).

## Doğrulandı
- CI: Android derleme + lint, PC derleme + uçtan uca aktarım testi geçti (main'e itilen sürüm).
- Windows arayüzü, başsız ortamda ekran görüntüsü alınarak açık/koyu temada incelendi.

## Doğrulanmadı
- Android arayüzü gerçek telefonda görülmedi (yalnız CI'da derlendi, lint'ten geçti).
- Windows arayüzü gerçek Windows'ta (yazı tipi, ölçek) denenmedi.

## Public öncesi (27 Ekim)
- Geçmiş taraması: gerçek sır/anahtar yok (yalnız bilerek konan paylaşımlı imza). Commit yazarları noreply.
- THIRD_PARTY_NOTICES.md eklendi (zxing, zxing-android-embedded, Gradle: Apache 2.0); sürümlere de ekleniyor.
- Açık: LICENSE'ta tam ad var (karar kullanıcıda); Actions'taki eski LANBEAM_KEYSTORE_* secret'ları silinebilir.
- v0.2.0 Actions'tan elle yayınlandı (Run workflow + sürüm).

## Kalan / bilinen
- Etiket oturumdan itilemez (403): sürüm, Actions > Build > Run workflow ile yayınlanır.
- Eski kurulu APK farklı anahtarla imzalı: yeni sürüm üstüne kurulmaz, bir kez silip kurmak gerekir.
