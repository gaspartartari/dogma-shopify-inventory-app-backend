INSERT INTO tb_controlled_sku (sku, name) VALUES ('1204250_40961655799941','FROM REJECTION TO OBLIVION N2  EXTRA SPECIAL BITTER');
INSERT INTO tb_controlled_sku (sku, name) VALUES ('1204265_40956940746885','HOP LOVER');
INSERT INTO tb_controlled_sku (sku, name) VALUES ('1204266_40956938649733','SOURMIND MANGA E GOIABA');
INSERT INTO tb_controlled_sku (sku, name) VALUES ('1204319_40972310380677','METAMORPHOSE');
INSERT INTO tb_controlled_sku (sku, name) VALUES ('1204329_40961602846853','FROM REJECT TO OBLIVION N6  DRY STOUT');
INSERT INTO tb_controlled_sku (sku, name) VALUES ('12309_40972320047237','RED EYE');
INSERT INTO tb_controlled_sku (sku, name) VALUES ('1313_40956950937733','RIZOMA');
INSERT INTO tb_controlled_sku (sku, name) VALUES ('1324_40961985446021','MAGNUM OPUS');
INSERT INTO tb_controlled_sku (sku, name) VALUES ('13353_41174544220293','ABSENTIA');
INSERT INTO tb_controlled_sku (sku, name) VALUES ('1343_40956944679045','AMERICAN IPA');
INSERT INTO tb_controlled_sku (sku, name) VALUES ('13578_40972336300165','STRATA LOVER');
INSERT INTO tb_controlled_sku (sku, name) VALUES ('14050_40972330795141','SOURMIND PITAIA CAJU E MARACUJÁ');
INSERT INTO tb_controlled_sku (sku, name) VALUES ('14528_40956939370629','HOP LITTLE LOVER');
INSERT INTO tb_controlled_sku (sku, name) VALUES ('14509_40952603738245','IPA');
INSERT INTO tb_controlled_sku (sku, name) VALUES ('14530_40956932423813','PILSEN');
INSERT INTO tb_controlled_sku (sku, name) VALUES ('14965_41173439905925','HEFE WEIZEN');
INSERT INTO tb_controlled_sku (sku, name) VALUES ('1669_40956942418053','AMERICAN PALE ALE');
INSERT INTO tb_controlled_sku (sku, name) VALUES ('447_40972342296709','REST IN PILZ');
INSERT INTO tb_controlled_sku (sku, name) VALUES ('6600_41368920588421','BOURBON VANILLA EUDER'); /*Inventory item ID 43458628583557 */
INSERT INTO tb_controlled_sku (sku, name) VALUES ('3636_40972318441605','PROTOSTAR'); /*Inventory item ID 43055407169669 */

-- Security seed
INSERT INTO tb_role (id, authority) VALUES (1, 'ROLE_ADMIN');
INSERT INTO tb_role (id, authority) VALUES (2, 'ROLE_OPERATOR');

-- password: admin (BCrypt)
INSERT INTO tb_user (email, password) VALUES( 'gaspar@tartari.com', '$2a$10$k0qK5Rnu8mGN09KrCRNRHOaI3VkvenhNnt7iXPERNumfDY/uTinoi');
INSERT INTO tb_user (email, password) VALUES( 'lojavirtual@cervejariadogma.com.br ', '$2a$10$GsXkevlwMvEbxegWJqAPIOXVgOzqqHLbGWA.aG3118s6UFiGIU5q2');

INSERT INTO tb_user_role (user_id, role_id) VALUES (1, 1);
INSERT INTO tb_user_role (user_id, role_id) VALUES (2, 1);