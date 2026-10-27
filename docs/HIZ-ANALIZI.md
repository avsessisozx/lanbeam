# Hız analizi: SuperBeam incelemesi ve LanBeam ölçümleri

27 Ekim 2026, yerel oturumda (gerçek PC + gerçek telefon) yapıldı. Bulut oturumları SuperBeam
programına, telefona ve ev ağına erişemediği için bulgular buraya yazıldı.

## Kısa sonuç

1. **SuperBeam PC'de gizli bir hız tekniği yok.** Tek HTTP bağlantısında ham akış kullanıyor;
   LanBeam bunu zaten aynen yapıyor, tamponları da daha büyük.
2. **LanBeam ağın taşıyabildiğinin ~%95'ini kullanıyor.** Aynı anda ölçülen ham ağ hızı 47 MB/s iken
   uygulama 45 MB/s aldı. Uygulamada düzeltilecek büyük bir darboğaz yok.
3. **Hızı belirleyen Wi-Fi / modem.** Aynı telefon, aynı PC, aynı sinyal (-40 dBm, 1200 Mbit/s):
   26 Ekim'de 89,9 MB/s, 27 Ekim'de 34–50 MB/s. Fark ağ ortamından geliyor, koddan değil
   (aktarım döngüsü iki gün arasında değişmedi; yalnızca ölçüm satırları eklendi).
4. **Uygulamadaki hız testi gerçek hızı düşük gösteriyor** (Wi-Fi yüksek performans kilidi almıyor,
   test çok kısa). Raporlardaki "ağ testi 37,8 MB/s" gibi değerler bu yüzden aktarımdan düşük.
5. **SuperBeam'in asıl hız avantajı telefon ↔ telefon aktarımında:** iki telefonu router üzerinden değil,
   doğrudan (Wi-Fi Direct) bağlıyor. Router üzerinden giden her paket havada iki kez taşınır, hız
   yaklaşık yarıya iner. LanBeam'de bunun karşılığı "Router olmadan" (hotspot) modu.

## 1. SuperBeam PC (v1.2.0) incelemesi

Kaynak: `superbeam.exe`, içinde gömülü bir Java uygulaması (Launch4j). `javap` ile bytecode incelendi.
Aşağıdakiler kodda doğrudan görülen değerlerdir.

### Aktarım yöntemi
- Gönderen 8080'de bir HTTP sunucusu (Apache HttpCore) açar.
- Alıcı önce dosya listesini (ad, boyut, klasör) alır, sonra **tek bir isteğe** tüm dosyalar art arda,
  aralarında ayırıcı olmadan ham bayt olarak gelir. Alıcı akışı boyutlara göre keserek dosyalara böler.
- Dosya başına istek yoktur; binlerce küçük dosyada da hız düşmez. **LanBeam'in `/s/<token>/stream`
  ucu aynı yöntemdir.**

### Hızla ilgili ayarlar (SuperBeam ↔ LanBeam)

| Ayar | SuperBeam PC | LanBeam |
|---|---|---|
| Bağlantı sayısı | 1 | 1 |
| Gönderen okuma tamponu | 128 KB | 1 MB (`Protocol.COPY_BUFFER`) |
| Gönderen soket/oturum tamponu | 256 KB (`http.socket.buffer-size`) | 1 MB `SO_SNDBUF` |
| `TCP_NODELAY` | açık | kapalı (büyük yazımlarda fark etmez) |
| Alıcı okuma tamponu | **16 KB** + 8 KB `BufferedInputStream` | 1 MB |
| Alıcı yazma | `FileOutputStream` + 8 KB `BufferedOutputStream` | 1 MB `BufferedOutputStream` |
| Zaman aşımı | 7 sn | bağlanma 8 sn, okuma 30 sn |
| Sunucu iş parçacığı | bağlantı başına bir thread | önbellekli thread havuzu |

Sonuç: SuperBeam PC'nin ayarları sıradan, bazıları LanBeam'den küçük. **Kopyalanacak bir hız ayarı yok.**

### İncelenemeyenler
- **SuperBeam Android uygulaması** bu bilgisayarda ve telefonda yok. Android tarafı (Wi-Fi Direct
  kurulumu, Wi-Fi kilitleri, paralel bağlantı kullanıp kullanmadığı) bilinmiyor. Telefona SuperBeam
  kurulursa APK yerel oturumda çekilip incelenebilir.
- SuperBeam ile LanBeam aynı ağda yan yana ölçülmedi.

## 2. Ölçümler (27 Ekim)

Ortam: PC kablolu 1 Gbit/s (Realtek, 192.168.1.13). Telefon Mi 10T Pro, Android 12, Wi-Fi 6,
5240 MHz, RSSI -40 dBm, bağlantı 1200 Mbit/s. Modem: Superonline. 800 MB rastgele veri.

| Test (PC → telefon) | Uygulama devrede mi | Hız |
|---|---|---|
| `curl` → `/dev/null` (sadece ağ) | hayır | 39,4 / 38,6 MB/s |
| `curl` → `/dev/null`, daha sonra | hayır | 47,4 MB/s |
| `curl` 48 MB kısa test | hayır | 42,7 MB/s |
| `curl` → `/sdcard` dosyası (ağ + depolama) | hayır | 38,8 MB/s |
| `curl` 2 paralel bağlantı | hayır | 50,1 / 34,0 MB/s (toplam) |
| **LanBeam uygulaması (Wi-Fi kilidi açık)** | evet | **45,0 MB/s** |
| LanBeam uygulaması, 26 Ekim | evet | 89,9 MB/s |

Okuma:
- **Depolama darboğaz değil:** dosyaya yazınca da hız aynı (38,8 ve 39 MB/s).
- **Paralel bağlantı işe yaramıyor:** 2 bağlantı toplamı tek bağlantıdan anlamlı yüksek değil. Sınır
  tek TCP akışı değil, kablosuz bağlantının kendisi. (Bulutun v0.3.2'deki ölçümüyle de uyumlu.)
- **Wi-Fi kilidi biraz yardımcı:** kilitsiz `curl` ~39, kilitli uygulama 45 MB/s.
- **Ağ zamana göre çok değişiyor** (34–90 MB/s). Modemin yükü, kanal, diğer cihazlar belirleyici.
- Paralel `curl` testinde bir bağlantının yarıda kesilmesi görüldü; tekrar testinde iki bağlantı da
  800 MB'ı eksiksiz aldı. Sebep, `adb shell` kapanınca arka plandaki işlemlerin öldürülmesiydi
  (ölçüm hatası, sunucu hatası değil).

## 3. Yapılacaklar (öncelik sırasıyla)

> **Durum (v0.4.0):** 3.1 ve 3.2 yapıldı. Ayrıntılar ve ölçümler en altta, "v0.4.0'da yapılanlar" bölümünde.

### 3.1 Hız testini düzelt (yanlış ölçüyor)
`core/.../SpeedTest.java` ve `android/.../MainActivity.java` (satır ~378):
- Test `MainActivity` içinde çalışıyor; **WifiLock (HIGH_PERF + LOW_LATENCY) ve WakeLock almıyor.**
  Aktarımlar bu kilitleri `LanBeamService.lock()` ile alıyor, test almıyor. Test sırasında da alınmalı
  (testi servise taşımak ya da `acquireTransferLocks()`'ı test etrafında çağırmak).
- 48 MB (paralelde 32 MB) çok kısa: Wi-Fi hız uyarlaması ve TCP yavaş başlangıcı ilk saniyeyi yiyor.
  **Süreye bağlı** test (ör. 5 sn) yapılmalı ve ilk ~1 sn hesaba katılmamalı.
- PC tarafında ölçülen hızla telefon tarafında ölçülen hız ayrı raporlanmalı.

### 3.2 Telefon ↔ telefon: doğrudan bağlantıyı tercih et (asıl büyük kazanç)
İki telefon aynı router'daysa veri telefon → router → telefon gider; havada iki kez taşındığı için
hız yaklaşık yarıya iner. SuperBeam bu yüzden telefonlar arasında Wi-Fi Direct kullanıyor.
- LanBeam'de "Router olmadan" (LocalOnlyHotspot) modu zaten var. Telefon ↔ telefon aktarımında
  bunu öne çıkarmak, büyük aktarımlarda önermek ya da otomatik denemek en büyük hız kazancını verir.
- Wi-Fi Direct (`WifiP2pManager`) alternatifi: hotspot'tan farklı olarak alıcının normal Wi-Fi
  bağlantısını koparmayabilir, ama cihazlar arası uyumluluğu değişken.
- Ölçülmedi: iki telefon gerekli.

### 3.3 Yapılmaması gerekenler
- **Paralel bağlantı eklemek:** ölçümler kazanç göstermiyor; karmaşıklık ve hata riski getirir.
- **Tampon boyutlarını büyütmek:** 1 MB yeterli; SuperBeam 16–256 KB ile çalışıyor.
- **Depolama yolunu değiştirmek:** MediaStore/dosya yazma darboğaz değil.

### 3.4 Kullanıcıya gösterilecek ipuçları (uygulamanın değiştiremeyeceği şeyler)
- Telefon modeme yakın, 5 GHz ağda olsun; mümkünse modemde 160 MHz kanal genişliği açık olsun.
- Aktarım sırasında aynı ağda büyük indirme veya yayın olmasın.
- PC mümkünse kabloyla bağlı olsun (bu testte öyleydi): o zaman havadaki tek bağlantı telefonunki olur.

## 4. Ölçümü tekrarlamak

PC'de bir paylaşım açıp telefonda uygulamasız ham hızı ölçmek için (telefon USB hata ayıklamada):
```bash
adb shell "curl -s -o /dev/null -w '%{size_download} B, %{speed_download} B/s\n' http://<PC-IP>:<port>/s/<token>/stream"
```
Aynı dosyayı LanBeam ile gönderip iki sonucu karşılaştırın: uygulama hızı `curl` hızına yakınsa
sınır ağdır, uygulamada aranacak bir şey yoktur.

## 5. v0.4.0'da yapılanlar (27 Ekim, yerel oturum, gerçek telefonla denendi)

### Hız testi (`core/.../SpeedTest.java`)
- **Süreye bağlı:** her ölçüm 1 sn ısınma + 4 sn sayım. Isınma sayılmaz; `MAX_BYTES` (4 GB) yalnızca üst sınır,
  test süre dolunca bağlantıyı kapatır. 1 bağlantı + 2 ve 4 paralel, toplam ~35 sn.
- **Wi-Fi kilidi:** telefonda test süresince `LanBeamService.beginSpeedTest()/endSpeedTest()` aktarımla aynı
  kilitleri alır. Başka bir cihaz bu telefona karşı test yaparken de `SpeedTest.serverHook` ile kilit alınır.
- Gerçek ölçüm (PC ↔ Mi 10T Pro, PC'den çalıştırılan yeni test): PC→telefon 46–53 MB/s,
  telefon→PC 72–77 MB/s (aynı anda `curl` 100 MB/s). İlk denemede telefon→PC 19,9 MB/s çıktı, tekrarlarda
  çıkmadı: ağ dakikalar içinde çok değişiyor, tek ölçüme güvenmeyin.
- Eski test aynı ağda 37,8 MB/s gösteriyordu.

### Telefon ↔ telefon doğrudan gönderim
- Telefonda bir Android cihaza dokununca seçim çıkar: **Doğrudan bağlantı** veya **Ev ağı üzerinden**.
- Doğrudan: gönderen `LocalOnlyHotspot` açar; teklif yine ev ağından gider ama içinde hotspot adı/şifresi
  vardır (`Offer` JSON'unda `ssid`/`pass`, protokolle geriye uyumlu). Alıcı kabul edince hotspot'a bağlanır
  (`WifiJoiner`, Android 10+), dosyaları doğrudan çeker, bitince ev ağına döner. Gönderen hotspot'u kapatır.
- **Yedek yol:** hotspot açılamazsa, teklif iletilemezse, izin verilmezse veya alıcı 30 sn içinde hotspot'a
  bağlanamazsa aktarım ev ağı üzerinden yapılır.
- Denendi: var olmayan ağ adıyla gönderilen doğrudan teklifte alıcı bağlanamadı, ev ağına geçti, 800 MB
  55,5 MB/s ile geldi, MD5 aynı. (Bekleme ilk denemede 60 sn idi ve takılmış gibi görünüyordu; 30 sn'ye
  indirildi ve durum satırı ne beklendiğini yazıyor.)
- **Denenmedi:** gerçek iki telefon arasında doğrudan aktarım ve hızı (ikinci telefon gerekli).
  LocalOnlyHotspot'un bandını (2,4/5 GHz) uygulama seçemez ve okuyamaz (`getBand()` sistem API'si);
  hotspot 2,4 GHz açılırsa doğrudan yol ev ağından yavaş olabilir. İki telefonla ölçülmeli.
