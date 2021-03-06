package cchesser.javaperf.workshop.data;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;

/**
 * An inefficient searcher for KCDC conference sessions and precompilers.
 */
public class Searcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(Searcher.class);

    // Injecting a synthetic bottleneck which only allows 50 requests per second. The
    // goals is to expose this on tests which throw more concurrent load at service.
    private static final RateLimiter THROTTLE = RateLimiter.create(50.0);
    private ConferenceSessionLoader loader;

    public Searcher(ConferenceSessionLoader loader) {
        this.loader = loader;
    }

    public SearchResult search(String term) {

        if (term == null || term.trim().isEmpty()) {
            LOGGER.debug("No query term supplied, returning empty results.");
            return new SearchResult();
        }

        THROTTLE.acquire();
        String normalizedTerm = term.toLowerCase();
        final List<ConferenceSession> content = loader.load();
        Iterable<ConferenceSession> filteredResults = Iterables.filter(content, new Predicate<ConferenceSession>(){
            @Override
            public boolean apply(@Nullable ConferenceSession input) {

                boolean tagsContainTerm = false;

                // Utilizing exceptions to expose exceptions thrown during profiling
                try {
                    for (String tag : input.getTags()) {
                        if (tag.toLowerCase().contains(normalizedTerm)) {
                            tagsContainTerm = true;
                            break;
                        }
                    }
                } catch (Exception ex) {
                    // For whatever reason, sometimes exceptions occur here. We want to log
                    // and ignore (treat them as not a pattern match for tags).
                    LOGGER.debug("Unable to search tags as part of the [{}] result (for query: [{}])", input.getTitle(), term);
                }

                return input.getTitle().toLowerCase().contains(normalizedTerm) ||
                        input.getAbstract().toLowerCase().contains(normalizedTerm) ||
                        tagsContainTerm;
            }
        });

        return new SearchResult(Lists.newArrayList(Iterables.transform(filteredResults, new Function<ConferenceSession, SearchResultElement>() {
            @Nullable
            @Override
            public SearchResultElement apply(ConferenceSession input) {
                return new SearchResultElement(input.getTitle(), input.getPresenter().getName(), input.getSessionType(), input.getAsciiArt());
            }
        })));
    }

    /**
     * Base type of results (single element of "results" with a list of conference results).
     */
    public static class SearchResult {

        private List<SearchResultElement> results;

        SearchResult() {
            this.results = Collections.emptyList();
        }

        SearchResult(List<SearchResultElement> results) {
            this.results = results;
        }

        public List<SearchResultElement> getResults() {
            return results;
        }
    }

    public static class SearchResultElement {

        private String title;
        private String presenter;
        private String sessionType;
        private String asciiArt;

        public SearchResultElement(String title, String presenter, String sessionType, String asciiArt) {
            this.title = title;
            this.presenter = presenter;
            this.sessionType = sessionType;
            this.asciiArt = asciiArt;
        }

        /**
         * @return Title of conference session.
         */
        public String getTitle() {
            return title;
        }

        /**
         * @return Full name of the presenter.
         */
        public String getPresenter() {
            return presenter;
        }

        /**
         * @return Label for session type (ex. regular session, 4-hour workshop).
         */
        public String getSessionType() {
            return sessionType;
        }

        @JsonIgnore
        public String getAsciiArt() { return asciiArt; }
    }
}
