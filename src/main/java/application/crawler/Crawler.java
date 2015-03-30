package application.crawler;

import application.crawler.domain.Domain;
import application.crawler.util.UrlQueue;
import service.messaging.MessagingService;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Crawler{
    private ExecutorService executor;
    private static UrlQueue domainQueue = new UrlQueue();
    private static HashMap<String, Domain> activelyCrawlingDomains = new HashMap<String, Domain>();
    private static UrlQueue crawledDomains = new UrlQueue();
    //TODO remove crawledDomains functionality, responsibility belongs to crawler hub now
    private static long timeAtBootUp;
    private static float lifeTime;
    private static float crawlRate;
    private static int totalCrawls;
    private int threadCount;
    private static float crawlRateTemp;
    private boolean running = true;
    private MessagingService messenger;
    private static final org.apache.log4j.Logger debugLog = org.apache.log4j.Logger.getLogger("pageErrorLogger");
    private static final org.apache.log4j.Logger errorLog = org.apache.log4j.Logger.getLogger("crawlerErrorLogger");

    public Crawler(int threadCount) {
        this.threadCount = threadCount;
        messenger = new MessagingService();
        executor = Executors.newFixedThreadPool(threadCount);
        timeAtBootUp = System.currentTimeMillis();
        requestCrawlableDomains();
    }

    public synchronized void crawl(){
        String newUrl = new String();
        while(running){
            if(domainQueue.getSize() > 0 && activelyCrawlingDomains.size() < threadCount){
                try {
                    newUrl = domainQueue.getNext();
                    Domain newDomain = new Domain(
                            new URL(newUrl)
                    );
                    activelyCrawlingDomains.put(newDomain.getDomainUrl(), newDomain);
                    executor.execute(new RunnableDomain(newDomain));
                } catch (MalformedURLException e) {
                    errorLog.warn("Malformed URL: " + newUrl + "\n" + e.getStackTrace().toString());
                }
            }

            if(domainQueue.getSize() < 2){
                requestCrawlableDomains();
            }
        }
    }

    private static void printCrawlRate(){
        lifeTime = (int) (System.currentTimeMillis() - timeAtBootUp);
        crawlRate = totalCrawls/(lifeTime/60000);
        System.out.println("Crawl rate: " + crawlRate + " crawls per minute" + " Total crawls: " + totalCrawls + " running for " + lifeTime / 1000 + " seconds");
    }

    private void requestCrawlableDomains(){
        List<String> newDomains = messenger.fetchFreshDomains();
        for(String domain : newDomains){
            domainQueue.enqueueUrl(domain);
        }
    }

    private void finalizeDomainCrawl(Domain crawledDomain){
        activelyCrawlingDomains.remove(crawledDomain.getDomainUrl());
        crawledDomains.enqueueUrl(crawledDomain.getDomainUrl());

        if(crawledDomain.getDiscoveredDomains().size() > 0){
            List<String> discoveredDomains = crawledDomain.getDiscoveredDomains();
            //messenger.publishMessage(String.join(";", discoveredDomains));
            messenger.publishDiscoveredDomains(discoveredDomains);
        }

        totalCrawls++;
        crawlRateTemp++;

        if(crawlRateTemp > 10 && totalCrawls > 0){
            printCrawlRate();
            crawlRateTemp = 0;
        }else{
            crawlRateTemp++;
        }
    }

    private class RunnableDomain implements Runnable{
        //TODO: remove me and refactor crawl to accept a Runnable lambda
        private Domain domain;

        public RunnableDomain(Domain newDomain){
            this.domain = newDomain;
        }

        public void run(){
            this.domain.run();
            finalizeDomainCrawl(domain);
        }

    }


}