package com.example.rxplayground

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.parallel.ParallelFlowable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.button_run_parallel
import kotlinx.android.synthetic.main.activity_main.button_run_parallel2
import kotlinx.android.synthetic.main.activity_main.button_run_parallel3
import kotlinx.android.synthetic.main.activity_main.button_run_step_by_step
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber


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
    }
}
