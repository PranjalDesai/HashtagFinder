package com.pranjaldesai.hashtagfinder;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import butterknife.BindView;
import butterknife.ButterKnife;
import de.hdodenhof.circleimageview.CircleImageView;
import io.realm.RealmResults;

public class SearchResultAdaptor extends RecyclerView.Adapter<SearchResultAdaptor.TweetViewHolder> {

    private RealmResults<Tweet> results;
    private int tag;
    private final String handlePrefix="@";

    public SearchResultAdaptor(RealmResults<Tweet> results) {
        this.results= results;
    }

    @NonNull
    @Override
    public TweetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        int layoutID = R.layout.tweet_list_item;
        LayoutInflater inflater= LayoutInflater.from(context);

        View view= inflater.inflate(layoutID, parent, false);
        return new TweetViewHolder(view, context);
    }

    @Override
    public void onBindViewHolder(@NonNull TweetViewHolder holder, int position) {
        holder.itemView.setTag(results.get(position).getCount_id());
        holder.bind(results.get(position));
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    public void removeData(){
        results=null;
    }

    public void setTag(int count_id){
        this.tag= count_id;
    }

    public int getTag(){
        return tag;
    }

    public void updateAdaptor(RealmResults<Tweet> newResults){
        results= newResults;
        notifyDataSetChanged();
    }


    class TweetViewHolder extends  RecyclerView.ViewHolder {

        @BindView(R.id.user_handle_text)
        TextView userHandle;
        @BindView(R.id.user_name_text)
        TextView userName;
        @BindView(R.id.user_tweet_description)
        TextView tweetDescription;
        @BindView(R.id.fav_count)
        TextView favCount;
        @BindView(R.id.retweet_count)
        TextView retweetCount;
        @BindView(R.id.user_verified)
        ImageView verified;
        @BindView(R.id.profile_image_view)
        CircleImageView profileView;
        Context mContext;

        public TweetViewHolder(View itemView, Context context){
            super(itemView);
            ButterKnife.bind(this, itemView);
            this.mContext= context;
        }

        public void bind(Tweet tweet) {
            userHandle.setText(handlePrefix.concat(tweet.getUser().getScreen_name()));
            userName.setText(tweet.getUser().getName());
            if(tweet.getUser().isVerified()){
                verified.setVisibility(View.VISIBLE);
            }else{
                verified.setVisibility(View.GONE);
            }
            try {
                Glide.with(mContext)
                        .load(tweet.getUser().getProfile_image_url_https())
                        .apply(new RequestOptions()
                                .placeholder(R.drawable.twitter_circle)
                                .fitCenter())
                        .into(profileView);
            }catch (Exception e){
                e.printStackTrace();
            }
            tweetDescription.setText(tweet.getText());
            favCount.setText(String.valueOf(tweet.getFavorite_count()));
            retweetCount.setText(String.valueOf(tweet.getRetweet_count()));
        }
    }
}
