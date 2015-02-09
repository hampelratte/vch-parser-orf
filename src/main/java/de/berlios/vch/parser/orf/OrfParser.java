package de.berlios.vch.parser.orf;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.ipojo.annotations.Bind;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Unbind;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osgi.service.log.LogService;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.net.INetworkProtocol;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.parser.IWebParser;
import de.berlios.vch.parser.OverviewPage;
import de.berlios.vch.parser.WebPageTitleComparator;

@Component
@Provides
public class OrfParser implements IWebParser {

    public static final String ID = OrfParser.class.getName();

    protected static final String BASE_URI = "http://tvthek.orf.at/service_api/token/027f84a09ec56b";
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

        aBisZ.put("0", createAlphaPage("0-9"));
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
        page.setUri(new URI("orf://aBisZ/" + character));
        page.setTitle(character);
        root.getPages().add(page);
        return page;
    }

    @Override
    public IOverviewPage getRoot() throws Exception {
        for (IOverviewPage alphaPage : aBisZ.values()) {
            alphaPage.getPages().clear();
        }

        String content = HttpUtils.get(BASE_URI + "/programs?page=0&entries_per_page=1000", null, CHARSET);
        JSONObject result = new JSONObject(content);
        JSONArray programShorts = result.getJSONArray("programShorts");
        for (int i = 0; i < programShorts.length(); i++) {
            JSONObject program = programShorts.getJSONObject(i);
            int episodeCount = program.getInt("episodesCount");
            if (episodeCount > 0) {
                IOverviewPage opage = new OverviewPage();
                opage.setParser(ID);
                opage.setTitle(program.getString("name"));

                int programId = program.getInt("programId");
                String pageUri = BASE_URI + "/episodes/by_program/" + programId + "?page=0&entries_per_page=1000";
                opage.setUri(new URI(pageUri));

                String firstCharacter = opage.getTitle().substring(0, 1).toLowerCase();
                if ("ö".equals(firstCharacter)) {
                    firstCharacter = "o";
                } else if ("ü".equals(firstCharacter)) {
                    firstCharacter = "u";
                } else if ("ä".equals(firstCharacter)) {
                    firstCharacter = "a";
                } else if (!firstCharacter.matches("[a-z]")) {
                    firstCharacter = "0";
                }

                IOverviewPage alphaPage = aBisZ.get(firstCharacter);
                if (alphaPage != null) {
                    alphaPage.getPages().add(opage);
                } else {
                    logger.log(LogService.LOG_WARNING, "No parent page for character " + firstCharacter);
                }
            }
        }

        // sort the alpha pages
        for (IOverviewPage alphaPage : aBisZ.values()) {
            Collections.sort(alphaPage.getPages(), new WebPageTitleComparator());
        }

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
            if ("orf".equals(page.getUri().getScheme())) {
                return page;
            } else if (page.getUri().toString().contains("/episode/")) {
                return new SegmentsParser(logger, supportedProtocols).parse(opage);
            } else {
                return parseOverviewPage(opage);
            }
        }

        return page;
    }

    private IWebPage parseOverviewPage(IOverviewPage opage) throws Exception {
        String content = HttpUtils.get(opage.getUri().toString(), null, CHARSET);
        JSONObject result = new JSONObject(content);
        JSONArray episodes = result.getJSONArray("episodeShorts");
        for (int i = 0; i < episodes.length(); i++) {
            JSONObject episode = episodes.getJSONObject(i);
            String url = episode.getString("detailApiCall");

            OverviewPage segmentsPage = new OverviewPage();
            segmentsPage.setTitle(episode.getString("title") + " - " + episode.getString("livedate"));
            segmentsPage.setParser(getId());
            segmentsPage.setUri(new URI(url));
            opage.getPages().add(segmentsPage);
        }
        return opage;
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