-- Je Bewertung einmal berechnete Auswertung (Stimmung, Emotion, Themen).
create table review_insights (
    review_id bigint not null,
    sentiment varchar(20) not null,
    score float(53) not null,
    emotion varchar(20) not null,
    source varchar(10) not null,
    analyzed_at timestamp(6) not null,
    primary key (review_id),
    constraint fk_review_insights_review foreign key (review_id) references product_reviews (id) on delete cascade
);

create table review_insight_aspects (
    review_id bigint not null,
    theme varchar(20) not null,
    sentiment varchar(20) not null,
    constraint fk_review_insight_aspects_insight foreign key (review_id) references review_insights (review_id) on delete cascade
);

create index idx_review_insight_aspects_review on review_insight_aspects (review_id);

-- Die frei formulierten KI-Trendberichte werden durch die Auswertung der gespeicherten Einordnungen ersetzt.
drop table if exists review_trend_reports;
