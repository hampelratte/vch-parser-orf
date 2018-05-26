package de.berlios.vch.parser.orf;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.apache.felix.ipojo.annotations.Bind;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Unbind;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.osgi.service.log.LogService;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.net.INetworkProtocol;
import de.berlios.vch.parser.HtmlParserUtils;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.parser.IWebParser;
import de.berlios.vch.parser.OverviewPage;
import de.berlios.vch.parser.VideoPage;

@Component
@Provides
public class OrfParser implements IWebParser {

    public static final String ID = OrfParser.class.getName();

    protected static final String BASE_URI = "http://tvthek.orf.at";
    protected static final String A_BIS_Z = BASE_URI + "/profiles/a-z";
    protected static final String CHARSET = "UTF-8";

    private Map<String, IOverviewPage> aBisZ = new HashMap<String, IOverviewPage>();

    private OverviewPage root = new OverviewPage();

    @Requires
    private LogService logger;

    List<String> supportedProtocols = new ArrayList<String>();

    public OrfParser() throws URISyntaxException {
        root = new OverviewPage();
        root.setParser(ID);
        root.setTitle(getTitle());
        root.setUri(new URI("vchpage://localhost/" + getId()));

        aBisZ.put("0", createAlphaPage("0"));
        aBisZ.put("a", createAlphaPage("A"));
        aBisZ.put("b", createAlphaPage("B"));
        aBisZ.put("c", createAlphaPage("C"));
        aBisZ.put("d", createAlphaPage("D"));
        aBisZ.put("e", createAlphaPage("E"));
        aBisZ.put("f", createAlphaPage("F"));
        aBisZ.put("g", createAlphaPage("G"));
        aBisZ.put("h", createAlphaPage("H"));
        aBisZ.put("i", createAlphaPage("I"));
        aBisZ.put("j", createAlphaPage("J"));
        aBisZ.put("k", createAlphaPage("K"));
        aBisZ.put("l", createAlphaPage("L"));
        aBisZ.put("m", createAlphaPage("M"));
        aBisZ.put("n", createAlphaPage("N"));
        aBisZ.put("o", createAlphaPage("O"));
        aBisZ.put("p", createAlphaPage("P"));
        aBisZ.put("q", createAlphaPage("Q"));
        aBisZ.put("r", createAlphaPage("R"));
        aBisZ.put("s", createAlphaPage("S"));
        aBisZ.put("t", createAlphaPage("T"));
        aBisZ.put("u", createAlphaPage("U"));
        aBisZ.put("v", createAlphaPage("V"));
        aBisZ.put("w", createAlphaPage("W"));
        aBisZ.put("x", createAlphaPage("X"));
        aBisZ.put("y", createAlphaPage("Y"));
        aBisZ.put("z", createAlphaPage("Z"));
    }

    private IOverviewPage createAlphaPage(String character) throws URISyntaxException {
        OverviewPage page = new OverviewPage();
        page.setParser(getId());
        page.setUri(new URI(BASE_URI + "/profiles/letter/" + character));
        page.setTitle(character);
        root.getPages().add(page);
        return page;
    }

    @Override
    public IOverviewPage getRoot() throws Exception {
        for (IOverviewPage alphaPage : aBisZ.values()) {
            alphaPage.getPages().clear();

        }

        // sort the alpha pages
        //        for (IOverviewPage alphaPage : aBisZ.values()) {
        //            Collections.sort(alphaPage.getPages(), new WebPageTitleComparator());
        //        }

        return root;
    }

    @Override
    public String getTitle() {
        return "ORF TVthek";
    }

    @Override
    public IWebPage parse(IWebPage page) throws Exception {
        logger.log(LogService.LOG_INFO, "Parsing page " + page.getUri().toString());
        if (page instanceof IOverviewPage) {
            IOverviewPage opage = (IOverviewPage) page;
            if (page.getUri().toString().contains("/letter/")) {
                return parseLetterPage(opage);
            } else if (page.getUri().toString().contains("/program/")) {
                return page;
            } else {
                return parseSegments(opage);
            }
        }

        return page;
    }

    private IWebPage parseLetterPage(IOverviewPage opage) throws Exception {
        String content = HttpUtils.get(opage.getUri().toString(), null, CHARSET);
        Elements programs = HtmlParserUtils.getTags(content, "div[class~=mod_results_list] ul li[class~=results_item]");
        for (Element element : programs) {
            String itemHtml = element.html();
            OverviewPage programPage = createProgramPage(itemHtml, opage);
            parseEpisodes(itemHtml, programPage);
        }
        return opage;
    }

    private OverviewPage createProgramPage(String itemHtml, IOverviewPage parent) throws Exception {
        OverviewPage programPage = new OverviewPage();
        programPage.setParser(getId());
        programPage.setTitle(HtmlParserUtils.getText(itemHtml, "h4.base_list_item_headline"));
        programPage.setParser(getId());
        programPage.setUri(new URI("orf://program/"+UUID.randomUUID().toString()));
        parent.getPages().add(programPage);
        return programPage;
    }

    private void parseEpisodes(String itemHtml, OverviewPage programPage) throws URISyntaxException {
        Elements episodeItems = HtmlParserUtils.getTags(itemHtml, "ul[class~=latest_episodes] li[class~=base_list_item]");
        for (Element element : episodeItems) {
            String episodeHtml = element.html();
            OverviewPage episodePage = new OverviewPage();
            episodePage.setParser(getId());
            Element link = HtmlParserUtils.getTag(episodeHtml, "a");
            episodePage.setTitle(link.attr("title"));
            Calendar publishDate = parsePublishDate(episodeHtml);
            episodePage.getUserData().put("publishDate", publishDate.getTimeInMillis());
            if(episodePage.getTitle().equalsIgnoreCase(programPage.getTitle())) {
                // same title -> use publish date instead
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
                episodePage.setTitle(sdf.format(publishDate.getTime()));
            }
            episodePage.setParser(getId());
            episodePage.setUri(new URI(link.attr("href")));
            programPage.getPages().add(episodePage);
        }
    }

    private Calendar parsePublishDate(String itemHtml) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(0);
        try {
            String time = HtmlParserUtils.getTag(itemHtml, "time").attr("datetime").trim();
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.ENGLISH);
            cal.setTime(sdf.parse(time));
        } catch (ParseException e) {
            logger.log(LogService.LOG_WARNING, "Couldn't parse publish date", e);
        }
        return cal;
    }

    private IWebPage parseSegments(IOverviewPage opage) throws Exception {
        String content = HttpUtils.get(opage.getUri().toString(), null, CHARSET);
        String playerJsonEncoded = HtmlParserUtils.getTag(content, "div[class~=jsb_VideoPlaylist]").attr("data-jsb");
        JSONObject result = new JSONObject(playerJsonEncoded);
        JSONObject playlist = result.getJSONObject("playlist");
        JSONArray videos = playlist.getJSONArray("videos");
        for (int i = 0; i < videos.length(); i++) {
            JSONObject video = videos.getJSONObject(i);
            VideoPage videoPage = new VideoPage();
            videoPage.setParser(getId());
            videoPage.setTitle(video.getString("title"));
            videoPage.setDuration(video.getLong("duration") / 1000);
            videoPage.setUri(new URI(opage.getUri().toString() + "#" + video.getString("hash")));
            Calendar publishDate = Calendar.getInstance();
            publishDate.setTimeInMillis((Long) opage.getUserData().get("publishDate"));
            videoPage.setPublishDate(publishDate);
            if(!video.isNull("description")) {
                videoPage.setDescription(video.getString("description"));
            }
            if(!video.isNull("preview_image_url")) {
                videoPage.setThumbnail(new URI(video.getString("preview_image_url")));
            }
            videoPage.setVideoUri(getBestVideoUri(video));
            opage.getPages().add(videoPage);
        }
        return opage;
    }

    private URI getBestVideoUri(JSONObject video) throws JSONException, URISyntaxException {
        Map<String, Integer> qualityPrio = new HashMap<String, Integer>();
        qualityPrio.put("Niedrig", 0);
        qualityPrio.put("Mittel", 1);
        qualityPrio.put("Hoch", 2);
        qualityPrio.put("Sehr hoch", 3);

        String bestUri = "";
        int bestPrio = -1;
        JSONArray sources = video.getJSONArray("sources");
        for (int i = 0; i < sources.length(); i++) {
            JSONObject source = sources.getJSONObject(i);
            if(source.getString("delivery").equals("progressive") && source.getString("protocol").startsWith("http")) {
                String qualityString = source.getString("quality_string");
                if(qualityPrio.containsKey(qualityString)) {
                    int prio = qualityPrio.get(qualityString);
                    if(prio > bestPrio) {
                        bestPrio = prio;
                        bestUri = source.getString("src");
                    }
                } else {
                    continue;
                }
            }
        }
        return new URI(bestUri);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Bind(id = "supportedProtocols", aggregate = true)
    public synchronized void addProtocol(INetworkProtocol protocol) {
        supportedProtocols.addAll(protocol.getSchemes());
    }

    @Unbind(id = "supportedProtocols", aggregate = true)
    public synchronized void removeProtocol(INetworkProtocol protocol) {
        supportedProtocols.removeAll(protocol.getSchemes());
    }
}