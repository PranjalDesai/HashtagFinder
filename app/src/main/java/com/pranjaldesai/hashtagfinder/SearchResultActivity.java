package com.pranjaldesai.hashtagfinder;

import android.graphics.Canvas;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.TwitterApiClient;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.models.Search;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator;

public class SearchResultActivity extends AppCompatActivity {

    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.tweet_list)
    RecyclerView mRecyclerView;
    @BindView(R.id.searchProgress)
    ProgressBar progressBar;
    @BindView(R.id.tweet_error_text)
    TextView errorView;

    private Realm realm;
    RealmResults<Tweet> sortedQuery;
    private SearchResultAdaptor searchResultAdaptor;
    String userNameQuery;
    private final String HASHTAG= "%23";

    private int currentMax=15;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_result);

        ButterKnife.bind(this);
        realm = Realm.getDefaultInstance();

        // Get extra search hashtag from intent
        userNameQuery= getIntent().getStringExtra(getResources().getString(R.string.hashtag_extra));

        if(savedInstanceState!=null){
            currentMax= savedInstanceState.getInt(getResources().getString(R.string.current_max));
        }

        //set up Toolbar
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null && userNameQuery!= null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(URLDecoder.decode(HASHTAG).concat(userNameQuery));
        }

        if(userNameQuery!=null){
            toolbar.setTitle(URLDecoder.decode(HASHTAG).concat(userNameQuery));
            // Query that gets result for top 15 tweets from the database.
            sortedQuery = realm.where(Tweet.class).between(getResources().getString(R.string.count_id_text), 0, currentMax).findAll();
            if(sortedQuery!=null && sortedQuery.size()!=0){
                searchResultAdaptor= new SearchResultAdaptor(sortedQuery);
                mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                mRecyclerView.setAdapter(searchResultAdaptor);

                itemTouchHelper();
            }else{
                errorView.setVisibility(View.VISIBLE);
            }
        }
    }


    //touch to swipe left and delete
    private void itemTouchHelper(){
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT ) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int selectedID= (int) viewHolder.itemView.getTag();
                //gets the position to delete from the id and deletes the tweet from the database
                Tweet position= sortedQuery.where().equalTo(getResources().getString(R.string.count_id_text), selectedID).findFirst();
                realm.beginTransaction();
                if (position != null) {
                    position.deleteFromRealm();
                }
                realm.commitTransaction();

                //if tweets are less than 10 than it makes call for 5 more tweets
                int size= searchResultAdaptor.getItemCount();
                if(size<=10){
                    fetchMoreTweet();
                    progressBar.setVisibility(View.VISIBLE);
                }else{
                    sortedQuery = realm.where(Tweet.class).between(getResources().getString(R.string.count_id_text), 0, currentMax).findAll();
                    searchResultAdaptor.updateAdaptor(sortedQuery);
                }

            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                new RecyclerViewSwipeDecorator.Builder(SearchResultActivity.this, c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                        .addSwipeLeftBackgroundColor(ContextCompat.getColor(SearchResultActivity.this, R.color.colorAccent))
                        .addSwipeLeftActionIcon(R.drawable.delete_sweep)
                        .create()
                        .decorate();
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        }).attachToRecyclerView(mRecyclerView);
    }

    private void fetchMoreTweet(){
        final Tweet tweet= realm.where(Tweet.class).sort(getResources().getString(R.string.count_id_text), Sort.DESCENDING).findFirst();
        long maxID = tweet.getId();
        final int maxCountID= tweet.getCount_id();

        //finds tweet from the last maxid so there are no repeats
        TwitterApiClient twitterApiClient= TwitterCore.getInstance().getApiClient();
        String queryString= HASHTAG.concat(userNameQuery);
        twitterApiClient.getSearchService().tweets(queryString,null,null,
                null, null,15,null,null, maxID,true)
                .enqueue(new Callback<Search>() {
            @Override
            public void success(Result<Search> result) {
                if(result!= null && result.data!=null) {
                    //Converts from Twitter Tweet format to realmObjects
                    ArrayList<Tweet> tweets = convertToTweetList(result.data.tweets, maxCountID);
                    //if not tweets are fetched then gets local tweets that are already loaded in the database
                    if(tweets.size()==0){
                        fetchFiveMoreTweets();
                    }else {
                        addToDatabase(tweets);
                        fetchFiveMoreTweets();
                    }
                }
            }

            @Override
            public void failure(TwitterException exception) {
                Snackbar.make(findViewById(R.id.SearchResult),exception.getMessage(), Snackbar.LENGTH_LONG).show();
                progressBar.setVisibility(View.GONE);
                sortedQuery = realm.where(Tweet.class).between(getResources().getString(R.string.count_id_text), 0, currentMax).findAll();
                searchResultAdaptor.updateAdaptor(sortedQuery);
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(getResources().getString(R.string.current_max), currentMax);
        super.onSaveInstanceState(outState);
    }

    // fetches the next 5 tweets from the database
    private void fetchFiveMoreTweets(){
        currentMax+=5;
        sortedQuery = realm.where(Tweet.class).between(getResources().getString(R.string.count_id_text), 0, currentMax).findAll();
        if(sortedQuery != null){
            searchResultAdaptor.updateAdaptor(sortedQuery);
            progressBar.setVisibility(View.GONE);
        }else{
            Snackbar.make(findViewById(R.id.SearchResult),getResources().getString(R.string.tweet_not_found_error), Snackbar.LENGTH_LONG).show();
        }
    }

    //Converts from Twitter Tweet format to realmObjects
    private ArrayList<Tweet> convertToTweetList(List<com.twitter.sdk.android.core.models.Tweet> tweetData, int currentCountID){
        ArrayList<Tweet> databaseList= new ArrayList<>();
        int i= currentCountID+1;
        for(com.twitter.sdk.android.core.models.Tweet singleTweet: tweetData){
            Tweet realmTweet= new Tweet();
            realmTweet.setFavorite_count(singleTweet.favoriteCount);
            realmTweet.setId(singleTweet.id);
            realmTweet.setRetweet_count(singleTweet.retweetCount);
            realmTweet.setText(singleTweet.text);
            User user= new User();
            user.setName(singleTweet.user.name);
            user.setProfile_image_url_https(singleTweet.user.profileImageUrlHttps);
            user.setScreen_name(singleTweet.user.screenName);
            user.setVerified(singleTweet.user.verified);
            realmTweet.setUser(user);
            realmTweet.setCount_id(i);
            databaseList.add(realmTweet);
            i++;
        }
        return databaseList;

    }

    // adds tweets to realm database
    private void addToDatabase(ArrayList<Tweet> databaseList){
        realm.beginTransaction();
        realm.insertOrUpdate(databaseList);
        realm.commitTransaction();
    }

    // Back arrow in app bar implementation
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
