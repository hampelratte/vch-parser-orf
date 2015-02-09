package de.berlios.vch.parser.orf;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.log.LogService;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IVideoPage;
import de.berlios.vch.parser.VideoPage;
import de.berlios.vch.parser.exceptions.NoSupportedVideoFoundException;

public class SegmentsParser {

    private LogService logger;

    private List<String> supportedProtocols;

    public SegmentsParser(LogService logger, List<String> supportedProtocols) throws URISyntaxException {
        this.logger = logger;
        this.supportedProtocols = supportedProtocols;
    }

    public IOverviewPage parse(IOverviewPage opage) throws Exception {
        String content = HttpUtils.get(opage.getUri().toString(), null, OrfParser.CHARSET);
        JSONObject result = new JSONObject(content);
        JSONObject episode = result.getJSONObject("episodeDetail");

        List<IVideoPage> segments = parseSegments(episode);
        for (IVideoPage video : segments) {
            video.setPublishDate(parseDate(episode));
            video.setUri(new URI(opage.getUri().toString() + "#segment_" + video.getUserData().get("segment")));
            opage.getPages().add(video);
        }
        return opage;
    }

    private List<IVideoPage> parseSegments(JSONObject episode) {
        List<IVideoPage> result = new ArrayList<IVideoPage>();
        try {
            JSONArray segments = episode.getJSONArray("segments");

            for (int i = 0; i < segments.length(); i++) {
                VideoPage videoPage = new VideoPage();
                videoPage.setParser(OrfParser.ID);
                URI streamingUri = null;
                URI bestUri = null;

                JSONObject segment = segments.getJSONObject(i);
                videoPage.getUserData().put("segment", segment.getInt("segmentId"));
                videoPage.setTitle(segment.getString("title"));
                videoPage.setDuration(parseDuration(segment));
                videoPage.setDescription(parseDescription(segment));
                videoPage.setThumbnail(parseThumbnail(segment));
                JSONArray videos = segment.getJSONArray("videos");

                // assuming that the videos are ordered by quality
                for (int j = 0; j < videos.length(); j++) {
                    JSONObject video = videos.getJSONObject(j);
                    String url = video.getString("streamingUrl");
                    if (url.contains(".m3u8")) {
                        // apples live streaming HLS is not yet supported
                        logger.log(LogService.LOG_DEBUG, "Ignoring HLS stream " + url);
                        continue;
                    }
                    streamingUri = new URI(url);
                    if (supportedProtocols.contains(streamingUri.getScheme())) {
                        bestUri = streamingUri;
                        if ("rtmp".equals(streamingUri.getScheme())) {
                            String rtmpUri = streamingUri.toString();
                            int pos = rtmpUri.indexOf("mp4:");
                            String streamName = rtmpUri.substring(pos);
                            videoPage.getUserData().put("streamName", streamName);
                        }
                    }
                }

                if (bestUri != null) {
                    logger.log(LogService.LOG_DEBUG, "Best video URI is " + bestUri);
                    videoPage.setVideoUri(bestUri);
                    result.add(videoPage);
                } else {
                    throw new NoSupportedVideoFoundException(streamingUri.toString(), supportedProtocols);
                }

            }

            return result;
        } catch (Exception e) {
            logger.log(LogService.LOG_WARNING, "Couldn't parse video", e);
        }

        throw new RuntimeException("No video found");
    }

    private URI parseThumbnail(JSONObject segment) {
        try {
            JSONArray images = segment.getJSONArray("images");
            if (images.length() > 0) {
                JSONObject thumbnail = images.getJSONObject(0);
                return new URI(thumbnail.getString("url"));
            }
        } catch (Exception e) {
            logger.log(LogService.LOG_WARNING, "Couldn't parse video thumbnail", e);
        }

        return null;
    }

    private String parseDescription(JSONObject segment) {
        String description = "";
        try {
            JSONArray descriptions = segment.getJSONArray("descriptions");
            for (int i = 0; i < descriptions.length(); i++) {
                JSONObject desc = descriptions.getJSONObject(i);
                String fieldName = desc.getString("fieldName");
                if ("small_teaser_text".equals(fieldName)) {
                    return desc.getString("text");
                }
            }
        } catch (JSONException e) {
            logger.log(LogService.LOG_WARNING, "Couldn't parse description", e);
        }
        return description;
    }

    private long parseDuration(JSONObject segment) {
        long duration = 0;
        try {
            duration = segment.getLong("duration");
        } catch (JSONException e) {
            logger.log(LogService.LOG_WARNING, "Couldn't parse duration", e);
        }
        return duration;
    }

    private Calendar parseDate(JSONObject episode) {
        // 10.08.2014 12:05:00
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        try {
            Date date = sdf.parse(episode.getString("livedate"));
            Calendar pubDate = Calendar.getInstance();
            pubDate.setTime(date);
            return pubDate;
        } catch (Exception e) {
            logger.log(LogService.LOG_WARNING, "Couldn't parse publish date", e);
        }
        return null;
    }
}