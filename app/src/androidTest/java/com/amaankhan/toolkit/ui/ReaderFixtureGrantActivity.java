package com.amaankhan.toolkit.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Standalone, dependency-free owner of the test provider's real Android URI grants. */
public final class ReaderFixtureGrantActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri uri = getIntent().getData();
        String target = getIntent().getStringExtra("target");
        if (uri == null || target == null) throw new IllegalArgumentException("Missing fixture request");
        if (getIntent().getBooleanExtra("grant", false)) {
            try {
                createDocuments();
            } catch (IOException error) {
                throw new IllegalStateException("Could not create reader fixtures", error);
            }
            if (getIntent().getBooleanExtra("result", false)) {
                setResult(RESULT_OK, new Intent().setData(uri).addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION));
            } else {
                grantUriPermission(target, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        } else {
            // Also revoke Activity-result grants; the package overload only revokes
            // grants made directly with grantUriPermission.
            revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        finish();
    }

    private void createDocuments() throws IOException {
        try (FileOutputStream output = new FileOutputStream(new File(getCacheDir(), "synthetic.txt"))) {
            output.write("First part\n".getBytes(StandardCharsets.UTF_8));
            byte[] line = "Synthetic text. ".getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 6000; i++) output.write(line);
            output.write("\nLast part".getBytes(StandardCharsets.UTF_8));
        }
        PdfDocument document = new PdfDocument();
        try {
            PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(300, 400, 1).create());
            document.finishPage(page);
            try (FileOutputStream output = new FileOutputStream(new File(getCacheDir(), "synthetic.pdf"))) {
                document.writeTo(output);
            }
        } finally {
            document.close();
        }
    }
}
