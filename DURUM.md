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

## Kalan / bilinen
- v0.2.0 etiketi oturumdan itilemedi (403); kullanıcı kendi bilgisayarından itecek: git tag v0.2.0 && git push origin v0.2.0
- Eski kurulu APK farklı anahtarla imzalı: yeni sürüm üstüne kurulmaz, bir kez silip kurmak gerekir.
