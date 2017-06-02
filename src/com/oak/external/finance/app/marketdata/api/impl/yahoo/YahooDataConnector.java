package com.oak.external.finance.app.marketdata.api.impl.yahoo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.oak.api.finance.model.Economic;
import com.oak.api.finance.model.Stock;
import com.oak.api.finance.model.dto.Quote;
import com.oak.external.finance.app.marketdata.api.DataConnector;

import yahoofinance.YahooFinanceWrapper;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;

public class YahooDataConnector implements DataConnector {
	private final Logger log;
	private final YahooDataConverter converter;
	private final YahooFinanceWrapper connector;

	public YahooDataConnector(Logger log, YahooDataConverter converter) {
		this.log = log;
		this.converter = converter;
		connector = new YahooFinanceWrapper(LogManager.getLogger(YahooFinanceWrapper.class));
	}

	@Override
	public Map<Stock, Map<Date, Economic>> getEconomics(Set<String> stocks) {
		Map<Stock, Map<Date, Economic>> ret = new HashMap<>();

		if (stocks != null && !stocks.isEmpty()) {
			ret = getEconomicsLoop(stocks);
		}
		return ret;
	}
	
	private Map<Stock, Map<Date, Economic>> getEconomicsLoop(Set<String> stocks) {
		HashMap<Stock, Map<Date, Economic>> ret = new HashMap<Stock, Map<Date, Economic>>();
		Collection<String[]> batches = batchStocks(stocks, 20);
		for (String[] batch : batches) {
			int attempt = 0;
			IOException e = getEconomicsForBatch(ret, batch);
			while(e != null) {
				String batchToStr = StringUtils.join(batch, ", ");
				if(attempt++ >= 5) {
					log.error("Giving up after failed to load maximum attemps: " + batchToStr,e);
					break;
				}
				e = getEconomicsForBatch(ret, batch);
				log.warn("retrying for the time n: " + (attempt + 1 ) + " loading batch: " + batchToStr);
			}
		}
		return ret;
	}

	private IOException getEconomicsForBatch(HashMap<Stock, Map<Date, Economic>> econMap, String[] batch) {
		IOException ret = null;
		try {
			Map<String, yahoofinance.Stock> map = null;
			map = connector.get(batch);
			Map<Stock, Map<Date, Economic>> yahooStockToEconomyPerDate = converter.yahooStockToEconomyPerDate(map);
			econMap.putAll(yahooStockToEconomyPerDate);
		} catch (IOException e) {
			log.error("error getting price from Yahoo for " + StringUtils.join(batch,", "));
			ret = e;
		}
		return ret;
	}
	
	

	private Collection<String[]> batchStocks(Set<String> stocks, int i) {
		Collection<String[]> batches = new ArrayList<String[]>();
		int j = 0;
		String[] stocksArray = new String[i];
		ArrayList<String> batchList = new ArrayList<String>(i);
		for (String s : stocks) {
			if (j >= i) {
				String[] array = batchList.toArray(stocksArray);
				batches.add(array);
				batchList = new ArrayList<String>(i);
				j = 0;
			}
			if(s!=null && !s.isEmpty()) {
				batchList.add(s);
			}
			j++;
		}
		stocksArray = new String [batchList.size()];
		String[] array = batchList.toArray(stocksArray);
		batches.add(array);
		return batches;
	}

	@Override
	public Map<String, Set<Quote>> getHistoricalQuotes(Set<String> tickers, Date fromDate) {
		String[] ticks = new String[tickers.size()];
		tickers.toArray(ticks);
		Calendar cal = Calendar.getInstance();
		cal.setTime(fromDate);
		Map<String, yahoofinance.Stock> map;
		Map<String, Set<Quote>> ret = new HashMap<>();
		try {
			map = connector.get(ticks, cal, Interval.DAILY);
			for (String ticker : map.keySet()) {
				HistoricalQuote q = null;
				Set<Quote> quotes = map.get(ticker).getHistory().stream()
						.map(s -> new Quote((Long) null, ticker, s.getOpen().doubleValue(), s.getClose().doubleValue(),
								s.getHigh().doubleValue(), s.getLow().doubleValue(), s.getVolume().longValue(),
								s.getDate().getTime()))
						.collect(Collectors.toSet());

				ret.put(ticker, quotes);
			}
		} catch (IOException e) {
			log.error("Error occured while getting historical quotes",e);
		}
		return ret;
	}

}
