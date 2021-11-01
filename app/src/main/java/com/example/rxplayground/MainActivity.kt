package com.example.rxplayground

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.button_run_parallel
import kotlinx.android.synthetic.main.activity_main.button_run_parallel2
import kotlinx.android.synthetic.main.activity_main.button_run_parallel3
import kotlinx.android.synthetic.main.activity_main.button_run_parallel_retry
import kotlinx.android.synthetic.main.activity_main.button_run_step_by_step
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.pow


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val okHttp = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

        val retrofit = Retrofit.Builder()
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .baseUrl("https://chunk-test-project.herokuapp.com/")
            .build()

        val endpoint = retrofit.create(Endpoint::class.java)
        button_run_step_by_step.setOnClickListener {
            button_run_step_by_step.isEnabled = false
            val listOfSingles = (1..3).map { endpoint.callEndpoint(it) }.toList()
            val call = Single.zip(listOfSingles) { args -> listOf(args) }
                .doOnSuccess {
                    Timber.d("Finished success")
                }
            Completable.fromSingle(call).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Timber.d("Network step finished")
                    button_run_step_by_step.isEnabled = true
                    Toast.makeText(this, "Network Finished", Toast.LENGTH_SHORT).show()
                }, { Timber.e(it) })
        }

        button_run_parallel.setOnClickListener {
            button_run_parallel.isEnabled = false
            val listOfSingles = (1..3).map { endpoint.callEndpoint(it) }.toList()
            val call = Observable.fromIterable(listOfSingles)
                .flatMap { it.toObservable().observeOn(Schedulers.io()) }
                .toList()
                .doOnSuccess { Timber.d("Finished Success") }
            Completable.fromSingle(call).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Timber.d("Network step finished")
                    button_run_parallel.isEnabled = true
                    Toast.makeText(this, "Network Finished", Toast.LENGTH_SHORT).show()
                }, { Timber.e(it) })
        }

        button_run_parallel2.setOnClickListener {
            button_run_parallel2.isEnabled = false
            Timber.d("number of Processors: ${Runtime.getRuntime().availableProcessors()}")
            val listOfSingles = (1..3).map { endpoint.callEndpoint(it).toObservable().subscribeOn(Schedulers.io()) }.toList()
            val call = Observable.merge(listOfSingles)
            Completable.fromObservable(call)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    Timber.d("Finished success")
                    button_run_parallel2.isEnabled = true
                }
        }

        button_run_parallel3.setOnClickListener {
            button_run_parallel3.isEnabled = false
            Completable.fromPublisher(
                Flowable.fromIterable(listOf(1, 2, 3)).parallel().flatMap { endpoint.callEndpoint(it).toFlowable().subscribeOn(Schedulers.io()) }
                    .sequential(2))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Timber.d("Network step finished")
                    button_run_parallel3.isEnabled = true
                    Toast.makeText(this, "Network Finished", Toast.LENGTH_SHORT).show()
                }, { Timber.e(it) })

        }

        button_run_parallel_retry.setOnClickListener {
            Timber.d("number of Processors: ${Runtime.getRuntime().availableProcessors()}")
            button_run_parallel_retry.isEnabled = false
            val executor = Executors.newFixedThreadPool(2)
            val pooledScheduler = Schedulers.from(executor)
            val listOfSingles =
                (3..4).map { number ->
                    endpoint.callEndpoint2(number)
                        .retryWhen { errors: Flowable<Throwable> ->
                            errors.zipWith(
                                Flowable.range(1, 3),
                                BiFunction { error: Throwable, retryCount: Int ->
                                    Timber.d("retryCount $retryCount")
                                    retryCount
                                }
                            ).flatMap { retryCount: Int ->
                                Flowable.timer(
                                    2.toDouble().pow(retryCount.toDouble()).toLong(),
                                    TimeUnit.SECONDS
                                )
                            }
                        }
                        .doOnSuccess { Timber.d("$it finished") }
                        .subscribeOn(pooledScheduler)
                }.toList()
            val call = Single.merge(listOfSingles)
            val valami = Completable.fromPublisher(call)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    button_run_parallel_retry.isEnabled = true
                    Timber.d("Finished success")
                }, { error ->
                    Timber.e(error, "Finished failed")
                    button_run_parallel_retry.isEnabled = true
                })
        }
    }

    override fun onResume() {
        super.onResume()
    }

    private fun magicFunction(number: Int): Int {
        if (number.rem(10) == 0) {
            throw Throwable("Just Exception")
        } else {
            return number
        }
    }
}
