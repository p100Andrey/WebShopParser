package main.java;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ShopParser {
    private static final String URL_FORMAT = "https://www.aboutyou.de/suche?category=20201&term=%s&page=%d";
    volatile int httpRequests = 0;
    volatile int prodAmount = 0;
    ThreadPoolExecutor executor = null;

    public List<Offer> getOffers(String searchString) {
        List<Offer> offers = new CopyOnWriteArrayList<>();

        try {
            int page = 1;
            int lastPage;
            Document doc = getDocument(searchString, page);

            Elements elements = doc.select("#pl-pager-bottom");
            if (elements.size() != 0) {
                Elements elems = elements.select(".gt9 a");
                if (elems.size() != 0) {
                    lastPage = Integer.parseInt(elems.first().html());
                } else {
                    Elements pages = elements.get(0).select(".page a");
                    if (pages.size() == 0) {
                        lastPage = 1;
                    } else {
                        lastPage = Integer.parseInt(pages.last().html());
                    }
                }
            } else {
                lastPage = 1;
            }

            executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
            List<Callable<List<Offer>>> taskList = new ArrayList<>();
            for (int i = 1; i < lastPage + 1; i++) {
                Task task = new Task(searchString, i);
                taskList.add(task);
            }
            List<Future<List<Offer>>> results = executor.invokeAll(taskList);
            for (Future<List<Offer>> result : results) {
                for (Offer offer: result.get()) {
                    offers.add(offer);
                }
            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            if (executor != null) {
                executor.shutdown();
            }
        }
        System.out.println("HTTP requests = " + httpRequests);
        System.out.println("Extracted products = " + prodAmount);
        return offers;
    }

    private Document getDocument(String searchString, int page) throws IOException {
        String url = String.format(URL_FORMAT, searchString, page);
        String userAgent = "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.106 Safari/537.36";
        Document doc = Jsoup.connect(url).userAgent(userAgent).referrer("http://google.com.ua").get();
        httpRequests++;
        return doc;
    }

    class Task implements Callable<List<Offer>> {
        private String searchString;
        private int page;

        public Task(String searchString, int page) {
            this.searchString = searchString;
            this.page = page;
        }

        @Override
        public List<Offer> call() throws Exception {
            ArrayList<Offer> offers = new ArrayList<>();
            try {
                Document doc = getDocument(searchString, page);

                Elements offersList = doc.getElementsByClass("row list-wrapper product-image-list");
                Elements elements = offersList.get(0).getElementsByAttributeValue("itemprop", "itemListElement");
                for (Element element : elements) {
                    Offer offer = new Offer();
                    offer.setName(element.getElementsByAttributeValue("itemprop", "name").text());
                    offer.setBrand(element.select(".js-product-item-brand").text());
                    Elements prices = element.getElementsByAttributeValue("itemprop", "price");
                    offer.setInitialPrice(prices.get(0).select("span").attr("data-price"));
                    if (prices.size() != 1) {
                        offer.setPrice(prices.get(1).select("span").attr("data-price"));
                    }
                    offer.setArticleId(element.select("article").attr("data-product-id"));

                    offers.add(offer);
                    prodAmount++;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return offers;
        }
    }
}