package main.java;

import java.io.IOException;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {

        long startTime = System.nanoTime();
        String term = "Jeans";

        ShopParser strategy = new ShopParser();
        List<Offer> offers = strategy.getOffers(term);
        XMLView xmlView = new XMLView();
        xmlView.update(offers);

        long runTime = (System.nanoTime() - startTime) / 1000000;
        System.out.println("Run-time = " + runTime + " ms");
    }
}