package com.jcy.client.model;

public final class PlayInfo {
    public final String title;
    public final String playUrl;
    public final boolean localPlaylist;

    public PlayInfo(String title, String playUrl, boolean localPlaylist) {
        this.title = title;
        this.playUrl = playUrl;
        this.localPlaylist = localPlaylist;
    }
}
