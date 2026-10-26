# LanBeam protokolü

LanBeam aynı yerel ağdaki cihazlar arasında dosya aktarır. Tasarımın merkezinde tek fikir var:
**dosya başına istek yapmadan, tüm dosyaları tek bir TCP bağlantısında art arda akıtmak.**

## Aktarım modeli
1. Alıcı gönderenden dosya listesini (göreli yol + boyut) alır.
2. Alıcı tek bir istekle **ham akışı** ister: tüm dosyalar listedeki sırayla, aralarında hiçbir
   ayırıcı veya başlık olmadan art arda gelir.
3. Alıcı akışı listedeki boyutlara göre keserek dosyalara böler.

Dosya başına istek/el sıkışma maliyeti olmadığı için binlerce küçük dosyada bile bağlantı hız sınırına ulaşılır.

## Keşif — UDP 45454
Her düğüm 2 sn'de bir yayın (broadcast) adreslerine JSON gönderir:
```json
{"app":"lanbeam","v":1,"id":"<uuid>","name":"Masaüstü","os":"windows","port":8080}
```
Yeni bir cihaz duyulunca ona hemen doğrudan cevap verilir; 7 sn boyunca duyulmayan cihaz listeden düşer.
Yayının engellendiği ağlarda cihaz IP ile elle eklenebilir (`GET /api/info`).

## HTTP — TCP 8080 (doluysa 8081…8089)
| Yöntem/Yol | Açıklama |
|---|---|
| `GET /api/info` | Cihaz bilgisi (keşifteki JSON) |
| `POST /api/offer` | Gönderen → alıcı: `{"fromId","fromName","port","token","count","total","first"}`. Gönderenin adresi TCP bağlantısından alınır. Alıcının kullanıcısı kabul ederse paylaşımı çeker. |
| `GET /s/<token>` | Tarayıcı sayfası (uygulaması olmayan cihazlar için) |
| `GET /s/<token>/list` | `{"files":[{"path":"klasör/ad.ext","size":123}],"total":...}` |
| `GET /s/<token>/stream` | Tüm dosyalar art arda ham bayt (`Content-Length` = toplam) |
| `GET /s/<token>/stream?from=<n>` | Toplam akışın n. baytından devam |
| `GET /s/<token>/file/<i>/<ad>` | Tek dosya, `Range` destekli |
| `GET /s/<token>/zip` | Hepsi tek ZIP (sıkıştırmasız, akışlı) |
| `POST /s/<token>/reject` | Alıcı teklifi reddetti |

Token 16 karakterlik rastgele bir dizgedir; token'ı bilmeyen kimse paylaşımdaki dosyalara erişemez.

## Paylaşım bağlantısı / QR
`http://<ip>:<port>/s/<token>` — tarayıcıda açılırsa indirme sayfası, LanBeam uygulamasında taranırsa doğrudan hızlı akış.

Router yokken (Android "hotspot modu") gönderen telefon yerel bir hotspot açar ve bağlantıya
`?ssid=<ad>&pass=<parola>` ekler. Alıcı (Android 10+) önce bu ağa bağlanır; sunucu adresi olarak
ağın ağ geçidini (hotspot'u açan telefon) kullanır.

## Hız ayarları
- 1 MB okuma/yazma tamponları ve 1 MB soket tamponları
- Tek bağlantı, dosya başına sıfır ek yük
- Android'de aktarım boyunca `WIFI_MODE_FULL_HIGH_PERF` / `WIFI_MODE_FULL_LOW_LATENCY` WifiLock ve WakeLock

## Güvenlik
- Trafik şifrelenmez; yalnızca güvenilen yerel ağlar için tasarlanmıştır.
- Her gelen aktarımı alıcının kullanıcısı onaylar.
- Ağdan gelen dosya yolları temizlenir: `..`, mutlak yollar ve Windows/Android'de geçersiz karakterler atılır.
