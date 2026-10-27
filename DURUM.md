# LanBeam — durum (27 Ekim 2026, v0.3.2 yayınlandı)

## Yapıldı
- Android ve Windows arayüzü yenilendi (kart düzeni, açık/koyu tema; Windows'ta Ayarlar'dan tema seçimi).
- İmza: depodaki sabit anahtar (android/keystore/lanbeam-shared.jks) ile; secret/yerel dosya gerekmez.
- Özgünlük taraması: başka uygulamadan kopya kod/isim bulunmadı (kendi protokolü, MIT).

## v0.3.0 (27 Ekim): hız testi ve rapor
- Android + PC: Wi-Fi bandı/hızı/sinyal gösterimi (2,4 GHz ve zayıf sinyalde ipucu), hız testi (ağ + depolama), "Rapor / Geri bildirim" (kopyala, paylaş, GitHub'da bildir).
- Her aktarımda ağ/depolama süresi ölçülür; rapor darboğazı söyler. Rapor dosya adı, IP, SSID, cihaz adı, anahtar içermez; kendiliğinden gönderilmez.
- Otomatik tampon/paralel bağlantı ayarı EKLENMEDİ: tek akış zaten bağlantıyı doyuruyor, ölçüm yapmadan değiştirmek riskli.

## v0.3.2 (27 Ekim)
- Android: çökme kaydı eklendi (sonraki açılışta rapora "Önceki çökme" olarak girer).
- BİLİNEN HATA: telefonda QR seçeneği (hangisi, "QR göster" mi "QR tara" mı belirsiz) uygulamayı çökertiyor; neden bilinmiyor, rapor bekleniyor.
- Paralel hız testi: PC→telefon yönünde paralel bağlantı hız artırmadı (47,6/44,0/41,3 MB/s), sınır Wi-Fi/yönlendirici; telefon→PC ölçümü tutarsız (tekrar gerekli). Paralel aktarım şimdilik eklenmedi.

## v0.3.1 (27 Ekim)
- Hız testine 1/2/4 paralel bağlantı ölçümü eklendi (tek akış mı sınır, görmek için). Gerçek telefonda sonucu henüz görülmedi.
- Gerçek ölçüm (Mi 10T Pro, 5 GHz, -39 dBm): ağ testi PC→telefon 37,8, telefon→PC 49,9 MB/s; aktarım ort. 44,4 MB/s; sınır Wi-Fi bağlantısı (disk değil).
- Sıradaki: paralel test tek akışın sınır olduğunu gösterirse paralel aktarım eklenecek.

## Doğrulandı
- v0.3.0: CI (Android derleme+lint, PC derleme+uçtan uca test: hız testi, depolama testi, rapor gizlilik kontrolü) geçti; Windows rapor penceresi başsız ekran görüntüsüyle bakıldı.
- CI: Android derleme + lint, PC derleme + uçtan uca aktarım testi geçti (main'e itilen sürüm).
- Windows arayüzü, başsız ortamda ekran görüntüsü alınarak açık/koyu temada incelendi.

## Doğrulanmadı
- Hız testi ve raporun gerçek Wi-Fi'de değerleri (Android Wi-Fi bilgisi, Windows netsh/PowerShell çıktısı) denenmedi.
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
