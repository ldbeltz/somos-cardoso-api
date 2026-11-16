CREATE TABLE secret_santa_words (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  locale VARCHAR(8) NOT NULL,
  word VARCHAR(64) NOT NULL,
  CONSTRAINT uq_secret_santa_word_locale UNIQUE (locale, word)
);

ALTER TABLE secret_santa_players
  ADD COLUMN secret_word VARCHAR(64) NULL,
  ADD CONSTRAINT uq_secret_santa_draw_word UNIQUE (ss_list_id, secret_word);

INSERT INTO secret_santa_words (locale, word) VALUES
('pt-BR', 'abacate'), ('pt-BR', 'abacaxi'), ('pt-BR', 'abelha'), ('pt-BR', 'abrigo'), ('pt-BR', 'açúcar'),
('pt-BR', 'alecrim'), ('pt-BR', 'algodão'), ('pt-BR', 'amora'), ('pt-BR', 'andorinha'), ('pt-BR', 'anel'),
('pt-BR', 'arara'), ('pt-BR', 'arco-íris'), ('pt-BR', 'areia'), ('pt-BR', 'aroeira'), ('pt-BR', 'azaleia'),
('pt-BR', 'baleia'), ('pt-BR', 'barco'), ('pt-BR', 'beija-flor'), ('pt-BR', 'bem-te-vi'), ('pt-BR', 'brisa'),
('pt-BR', 'caju'), ('pt-BR', 'caminho'), ('pt-BR', 'canela'), ('pt-BR', 'canto'), ('pt-BR', 'capivara'),
('pt-BR', 'carambola'), ('pt-BR', 'carinho'), ('pt-BR', 'cascata'), ('pt-BR', 'castanha'), ('pt-BR', 'cerejeira'),
('pt-BR', 'cigarra'), ('pt-BR', 'cipo'), ('pt-BR', 'cobre'), ('pt-BR', 'coelho'), ('pt-BR', 'colibri'),
('pt-BR', 'concha'), ('pt-BR', 'coruja'), ('pt-BR', 'cravo'), ('pt-BR', 'cupuaçu'), ('pt-BR', 'estrela'),
('pt-BR', 'farol'), ('pt-BR', 'favo'), ('pt-BR', 'floresta'), ('pt-BR', 'formiga'), ('pt-BR', 'framboesa'),
('pt-BR', 'girassol'), ('pt-BR', 'goiaba'), ('pt-BR', 'grilo'), ('pt-BR', 'ipe'), ('pt-BR', 'jabuticaba'),
('pt-BR', 'jandaia'), ('pt-BR', 'jardim'), ('pt-BR', 'jatobá'), ('pt-BR', 'jasmim'), ('pt-BR', 'laranja'),
('pt-BR', 'lavanda'), ('pt-BR', 'limoeiro'), ('pt-BR', 'lince'), ('pt-BR', 'lua'), ('pt-BR', 'luz'),
('pt-BR', 'manacá'), ('pt-BR', 'manga'), ('pt-BR', 'maracujá'), ('pt-BR', 'maré'), ('pt-BR', 'margarida'),
('pt-BR', 'melancia'), ('pt-BR', 'melodia'), ('pt-BR', 'mingau'), ('pt-BR', 'montanha'), ('pt-BR', 'neblina'),
('pt-BR', 'ninho'), ('pt-BR', 'nuvem'), ('pt-BR', 'oliva'), ('pt-BR', 'orvalho'), ('pt-BR', 'ouriço'),
('pt-BR', 'paineira'), ('pt-BR', 'pamonha'), ('pt-BR', 'pássaro'), ('pt-BR', 'pedra'), ('pt-BR', 'perola'),
('pt-BR', 'pessego'), ('pt-BR', 'pimenta'), ('pt-BR', 'pipoca'), ('pt-BR', 'pitanga'), ('pt-BR', 'praia'),
('pt-BR', 'quintal'), ('pt-BR', 'raposa'), ('pt-BR', 'riacho'), ('pt-BR', 'sereno'), ('pt-BR', 'vitoria'),
('pt-BR', 'baunilha'), ('pt-BR', 'borboleta'), ('pt-BR', 'cachoeira'), ('pt-BR', 'campina'), ('pt-BR', 'carvalho'),
('pt-BR', 'chuva'), ('pt-BR', 'diamante'), ('pt-BR', 'esquilo'), ('pt-BR', 'fogueira'), ('pt-BR', 'manjericao');
