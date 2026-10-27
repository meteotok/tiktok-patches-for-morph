package com.ss.android.ugc.aweme.feed.model;

import com.ss.android.ugc.aweme.base.model.UrlModel;

import java.util.List;

@SuppressWarnings("unused")
public class Video {
    // Raw backing fields, renamed in 47.5.3 (were bitRate, h264PlayAddr and playAddr on 46.2.3).
    public List<BitRate> bitRateList;
    public UrlModel downloadNoWatermarkAddr;
    public VideoUrlModel h264PlayAddrValue;
    public VideoUrlModel playAddrValue;

    public List<BitRate> getRawBitRate() {
        throw new UnsupportedOperationException("Stub");
    }
}
