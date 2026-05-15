package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

public class V91__decompress_editorial_content extends BaseJavaMigration {

    private static final String GZIP_BASE64_PREFIX = "H4sI";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, content FROM ad_placements WHERE kind = 'EDITORIAL' AND content IS NOT NULL");
             PreparedStatement update = connection.prepareStatement(
                "UPDATE ad_placements SET content = ? WHERE id = ?")) {

            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString(1);
                    String content = rs.getString(2);
                    if (content == null || !content.startsWith(GZIP_BASE64_PREFIX)) continue;

                    String decompressed = tryDecompress(content);
                    if (decompressed == null) continue;

                    update.setString(1, decompressed);
                    update.setString(2, id);
                    update.executeUpdate();
                }
            }
        }
    }

    private static String tryDecompress(String gzipBase64) {
        try {
            byte[] gzipped = Base64.getDecoder().decode(gzipBase64);
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(gzipped));
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[1024];
                int n;
                while ((n = gis.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } catch (Exception ignored) {
            return null;
        }
    }
}
