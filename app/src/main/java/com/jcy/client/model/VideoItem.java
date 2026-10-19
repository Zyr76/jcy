package com.jcy.client.model;

public final class VideoItem {
    public final String id;
    public final String title;
    public final String coverUrl;
    public final String subTitle;
    public final int currentEpisodes;
    public final int totalEpisodes;
    public final double score;

    public VideoItem(String id, String title, String coverUrl, String subTitle,
                     int currentEpisodes, int totalEpisodes, double score) {
        this.id = id;
        this.title = title;
        this.coverUrl = coverUrl;
        this.subTitle = subTitle;
        this.currentEpisodes = currentEpisodes;
        this.totalEpisodes = totalEpisodes;
        this.score = score;
    }

    public String episodeText() {
        if (totalEpisodes > 0) {
            return "更新至 " + currentEpisodes + " / " + totalEpisodes + " 集";
        }
        return currentEpisodes > 0 ? "更新至 " + currentEpisodes + " 集" : "";
    }
}
