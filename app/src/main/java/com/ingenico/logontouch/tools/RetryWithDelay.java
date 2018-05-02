package com.ingenico.logontouch.tools;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.functions.Function;

/**
 * Copyright (c) 2018 All Rights Reserved, Ingenico LLC.
 * <p>
 * Created by vigursky
 * Date: 02.05.2018
 */

public class RetryWithDelay implements Function<Observable<? extends Throwable>, Observable<?>> {
    private final int maxRetries;
    private final int retryDelayMillis;
    private int retryCount;

    public RetryWithDelay(final int maxRetries, final int retryDelayMillis) {
        this.maxRetries = maxRetries;
        this.retryDelayMillis = retryDelayMillis;
        this.retryCount = 0;
    }

    @Override
    public Observable<?> apply(final Observable<? extends Throwable> attempts) {
        return attempts
                .flatMap(new Function<Throwable, Observable<?>>() {
                    @Override
                    public Observable<?> apply(final Throwable throwable) {
                        if (++retryCount < maxRetries) {
                            // When this Observable calls onNext, the original
                            // Observable will be retried (i.e. re-subscribed).
                            return Observable.timer(retryDelayMillis,
                                    TimeUnit.MILLISECONDS);
                        }

                        // Max retries hit. Just pass the error along.
                        return Observable.error(throwable);
                    }
                });
    }
}