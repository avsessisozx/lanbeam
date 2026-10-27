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

> **Yalnızca bu sayfadan indirin.** İmza anahtarı herkese açıktır (açık kaynak); başka bir yerden gelen APK, gerçek uygulamanın üstüne kurulan değiştirilmiş bir kopya olabilir.

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

## Hız testi ve rapor

Ana ekrandaki **Bağlantı ve rapor** bölümü (PC'de **Bağlantı / Rapor** düğmesi) şunları yapar:

- Wi-Fi bandını (2,4 / 5 GHz), bağlantı hızını ve sinyali gösterir; 2,4 GHz veya zayıf sinyalde ipucu verir.
- **Hız testi:** seçtiğiniz cihazla ağ hızını ve depolama yazma hızını ölçer.
- Her aktarımda zamanın ağda mı depolamada mı geçtiğini ölçer ve raporda "darboğaz" yorumu verir.
- **Rapor / Geri bildirim:** cihaz modeli, bağlantı bilgisi, ölçümler ve son aktarımlar tek metindir; kopyalayıp geliştiriciye iletebilir veya GitHub'da sorun olarak açabilirsiniz. Dosya adı, IP, ağ adı ve paylaşım anahtarı içermez; kendiliğinden hiçbir yere gönderilmez.

Uygulama Wi-Fi bandını veya donanım sınırını değiştiremez; yalnızca neyin sınır olduğunu gösterir.

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
`v` ile başlayan bir etiket gönderildiğinde jar ve APK ile otomatik **Release** oluşturur:
```bash
git tag v0.2.0
git push origin v0.2.0
```

### İmzalama anahtarı
Tüm derlemeler depodaki sabit anahtarla (`android/keystore/lanbeam-shared.jks`) imzalanır; secret veya
bilgisayarda saklanan bir dosya gerekmez. Böylece her yeni sürüm eskisinin üzerine kurulur.
Bu anahtar herkese açıktır; yalnızca kişisel kullanım içindir, Play Store için kullanılmamalıdır.

## Güvenlik notları
- Paylaşımlar 16 karakterlik rastgele bir token ile korunur; her gelen aktarım alıcı tarafından onaylanır.
- Gelen dosya adları temizlenir (`..`, mutlak yol, geçersiz karakterler).
- Trafik şifrelenmez (düz HTTP). Yalnızca güvendiğiniz ev/ofis ağında kullanın.

## Lisans
[MIT](LICENSE). Kullanılan açık kaynak kütüphaneler: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
