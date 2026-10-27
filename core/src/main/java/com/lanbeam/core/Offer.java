package com.lanbeam.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** "I want to send you these files" — pushed from sender to receiver over POST /api/offer. */
public final class Offer {
    public final String fromId;
    public final String fromName;
    public final ShareLink link;
    public final int count;
    public final long total;
    /** First file name, for a friendly prompt ("photo.jpg and 3 more"). */
    public final String firstName;

    public Offer(String fromId, String fromName, ShareLink link, int count, long total, String firstName) {
        this.fromId = fromId;
        this.fromName = fromName;
        this.link = link;
        this.count = count;
        this.total = total;
        this.firstName = firstName;
    }

    String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fromId", fromId);
        m.put("fromName", fromName);
        m.put("port", link.port);
        m.put("token", link.token);
        m.put("count", count);
        m.put("total", total);
        m.put("first", firstName);
        if (link.ssid != null) {
            // Direct mode: the sender opened a hotspot; the receiver joins it instead of going through the router.
            m.put("ssid", link.ssid);
            m.put("pass", link.pass);
        }
        return Json.write(m);
    }

    /** The sender's host is taken from the TCP connection, which is always reachable from the receiver. */
    static Offer fromJson(String json, String senderHost) {
        Map<String, Object> m = Json.parseObject(json);
        ShareLink link = new ShareLink(senderHost, (int) Json.num(m, "port", Protocol.HTTP_PORT), Json.str(m, "token", ""),
                Json.str(m, "ssid", null), Json.str(m, "pass", null));
        return new Offer(Json.str(m, "fromId", ""), Json.str(m, "fromName", "?"), link,
                (int) Json.num(m, "count", 0), Json.num(m, "total", 0), Json.str(m, "first", ""));
    }

    public String describe() {
        String what = count <= 1 ? firstName : firstName + " ve " + (count - 1) + " dosya daha";
        return fromName + " → " + what + " (" + NetUtil.formatSize(total) + ")";
    }
}
