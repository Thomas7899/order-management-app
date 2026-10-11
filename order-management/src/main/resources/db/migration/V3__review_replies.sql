-- Antwort des Kundenservice auf eine Bewertung
alter table product_reviews add column reply_text text;
alter table product_reviews add column replied_at timestamp(6);
alter table product_reviews add column replied_by varchar(50);
