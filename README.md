# LanBeam

Aynı ağdaki (aynı modem/Wi-Fi) cihazlar arasında hızlı dosya aktarımı: **PC ↔ Android** ve
**Android ↔ Android**. Router yoksa Android telefonlar kendi hotspot'larını kurar.

[![Build](../../actions/workflows/build.yml/badge.svg)](../../actions/workflows/build.yml)

- Cihazlar birbirini otomatik bulur; dosyaları seçip karşı cihaza dokunmak yeterli, alıcı onaylar.
- Tüm dosyalar tek bir TCP bağlantısında art arda akar: dosya başına ek yük yok.
  Ölçülen: PC → Mi 10T Pro, 800 MB, Wi-Fi üzerinden **~90 MB/s**.
- Uygulaması olmayan cihazlar paylaşım bağlantısını tarayıcıda açıp indirebilir (tek tek veya ZIP).

Protokol: [PROTOCOL.md](PROTOCOL.md)

## İndirme
Hazır dosyalar **Releases** sayfasında: `LanBeam-x.y.z.jar` (PC) ve `LanBeam-x.y.z.apk` (Android 8.0+).

## Kullanım

### PC (Windows / Linux / macOS, Java 8+)
`LanBeam-x.y.z.jar` dosyasına çift tıklayın veya `java -jar LanBeam-x.y.z.jar`.
İlk açılışta Windows Güvenlik Duvarı izin isterse **Özel ağlar** için izin verin
(TCP 8080, UDP 45454); yoksa telefonlar PC'yi göremez.

- **Gönder:** Dosya/klasörleri pencereye sürükleyin → soldan cihazı seçin → *Seçili cihaza gönder*.
- **Al:** Gelen teklifi onaylayın; dosyalar `İndirilenler\LanBeam` klasörüne gelir (Ayarlar'dan değişir).
- **Bağlantı oluştur:** `http://ip:8080/s/...` adresi; tarayıcıdan indirilebilir.
- Cihaz listede yoksa *IP ile ekle*.

### Android
- Uygulama açıkken arka planda "alıma hazır" bildirimiyle çalışır; PC veya başka telefon her an dosya önerebilir.
- **Gönder:** *Dosya seç* / *Klasör seç* (veya herhangi bir uygulamada *Paylaş → LanBeam*) → listedeki cihaza dokunun.
- **QR / bağlantı:** Aynı ağdaki telefon *QR tara* ile alır, diğerleri tarayıcıdan.
- **Router olmadan:** Telefon yerel bir hotspot açar, SSID+şifreyi QR'a koyar; diğer telefon (Android 10+)
  *QR tara* yapınca bu ağa bağlanıp dosyaları çeker.
- Alınan dosyalar: `Download/LanBeam`.

## Geliştirme

```
core/      Ortak Java çekirdeği (protokol, HTTP sunucu, UDP keşif, alıcı) — PC ve Android aynen kullanır
pc/        Masaüstü uygulaması (Swing)
android/   Android uygulaması (minSdk 26)
```

PC (JDK 11+):
```bash
pc\build.bat test
```
Çıktı `dist\LanBeam.jar`; `test` uçtan uca aktarım testini de çalıştırır.

Android (JDK 17+, Android SDK; ya da klasörü Android Studio'da açın):
```bash
cd android && gradlew.bat assembleDebug
```

### Sürüm yayınlama
Her `main` gönderiminde GitHub Actions PC'yi derleyip testi çalıştırır, Android APK'sını derleyip lint'ten geçirir.
`v` ile başlayan bir etiket gönderildiğinde jar ve imzalı APK ile otomatik **Release** oluşturur:
```bash
git tag v0.2.0
git push origin v0.2.0
```

### İmzalama anahtarı (önemli)
Android güncellemeleri yalnızca **aynı anahtarla** imzalanırsa yüklenir. Anahtar repoda değildir:
- Yerelde: `%USERPROFILE%\.lanbeam\lanbeam-release.jks` + `keystore.properties` (şifreler).
  `android/keystore.properties` bu dosyayı gösterir (git'e girmez).
- GitHub'da: `LANBEAM_KEYSTORE_B64`, `LANBEAM_KEYSTORE_PASSWORD`, `LANBEAM_KEY_ALIAS`, `LANBEAM_KEY_PASSWORD` secret'ları.

`.lanbeam` klasörünü bilgisayar dışında güvenli bir yere (şifre yöneticisi, şifreli yedek) yedekleyin.
Kaybolursa yayınlanmış uygulama bir daha güncellenemez.

## Güvenlik notları
- Paylaşımlar 16 karakterlik rastgele bir token ile korunur; her gelen aktarım alıcı tarafından onaylanır.
- Gelen dosya adları temizlenir (`..`, mutlak yol, geçersiz karakterler).
- Trafik şifrelenmez (düz HTTP). Yalnızca güvendiğiniz ev/ofis ağında kullanın.

## Lisans
[MIT](LICENSE)
