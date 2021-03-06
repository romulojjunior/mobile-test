package com.eblushe.apptwitter.common.repositories

import com.eblushe.apptwitter.common.apis.twitter.mapToTweet
import com.eblushe.apptwitter.common.apis.twitter.services.AnalyzeSentimenService
import com.eblushe.apptwitter.common.apis.twitter.services.StatusesService
import com.eblushe.apptwitter.common.databases.dao.TweetDAO
import com.eblushe.apptwitter.common.models.Tweet
import com.eblushe.apptwitter.common.providers.ApiProvider
import com.eblushe.apptwitter.common.providers.RxProvider
import com.eblushe.apptwitter.common.providers.StorageProvider
import io.reactivex.Observable
import io.reactivex.Single

class TweetRepository(
    var statusesService: StatusesService,
    var analyzeSentimenService: AnalyzeSentimenService,
    var tweetDAO: TweetDAO,
    apiProvider: ApiProvider,
    storageProvider: StorageProvider,
    schedulerProvider: RxProvider
    ) : BaseRepository(apiProvider, storageProvider, schedulerProvider) {

    fun getUserTimeLine(screenName: String): Observable<List<Tweet>> {
        schedulerProvider.addDisposable(
            getUserTimeLineFromRemoteSource(screenName).subscribe(tweetDAO::saveAll)
        )

        return getUserTimeLineFromLocalSource(screenName)
    }

    private fun getUserTimeLineFromLocalSource(screenName: String): Observable<List<Tweet>> {
        return tweetDAO.findByName(screenName.toLowerCase())
            .subscribeOn(schedulerProvider.newThread())
            .observeOn(schedulerProvider.mainThread())

    }

    private fun getUserTimeLineFromRemoteSource(screenName: String): Single<List<Tweet>> {
        return statusesService.getUserTimeLine(screenName)
            .subscribeOn(schedulerProvider.newThread())
            .observeOn(schedulerProvider.newThread())
            .onErrorResumeNext { Single.just(emptyList()) }
            .map { items ->
                items.map(::mapToTweet)
            }
    }

    fun getTweetFeeling(tweet: Tweet) : Single<Tweet.Feeling> {
        return analyzeSentimenService.postFeeling(tweet.text!!)
            .subscribeOn(schedulerProvider.newThread())
            .observeOn(schedulerProvider.mainThread())
            .map { item ->
                    val score = item.DocumentSentimentResponse?.score!!
                when {
                    score > 0.6 -> Tweet.Feeling.POSITIVE
                    score <= 0.6 && score > 0 -> Tweet.Feeling.NEGATIVE
                    else -> Tweet.Feeling.NEUTRON
                }
            }
    }
}