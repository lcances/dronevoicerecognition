package com.dvr.mel.dronevoicerecognition;

import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.io.File;
import java.util.List;

public class FinalCorpusActivity extends AppCompatActivity {
    public Bundle b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_final_corpus);


        // get all the parameters
        b = getIntent().getExtras();
        String corpusName = b.getString("name");

        // change the first textView
        String textForLabel = "corpus " + corpusName + " enregistré";
        TextView label = (TextView) findViewById(R.id.label);
        label.setText(textForLabel);

        // check if one or more corpus are set as references
        // The first corpus is automatically set as reference
        TextView middleLabel = (TextView) findViewById(R.id.labelRecognition);

        if (CorpusInfo.referencesCorpora.isEmpty()) {
            Log.e("final activity", "aucune reference définie, ajout dans la liste");
            middleLabel = (TextView) findViewById(R.id.labelRecognition);
            middleLabel.setText("Aucune références à été définie");

            CorpusInfo.referencesCorpora.add(b.getString("name"));
            CorpusInfo.addCorpus(b.getString("name"), (Corpus) b.getSerializable("corpus"));

            CorpusInfo.saveToSerializedFile();
        }

        // yes - run the recognition code true all the references and display the succes percent
        else {
            // TODO : faire le systeme multi locuteur
            float percent = computeRecognitionRatio(
                    CorpusInfo.corpusGlobalDir.getAbsolutePath(),
                    CorpusInfo.referencesCorpora,
                    b.getString("name"));

            middleLabel.setText(Float.toString(percent));
        }
    }

    public void corpusFailHandler(View view) {
        // Suppression des fichiers du corpus et du dossier.
        // ==> Appeler la méthode CoprpusInfo.clean(String <nom du corpus>)
        CorpusInfo.clean(b.getString("name"));

        CorpusInfo.saveToSerializedFile();

        startActivity(new Intent(this, ManageCorpusesActivity.class));
    }

    public void corpusPassHandler(View view) {
        // Mettre à jours la class CorpusInfo.
        CorpusInfo.addCorpus(b.getString("name"), (Corpus) b.getSerializable("corpus"));

        CorpusInfo.saveToSerializedFile();

        startActivity(new Intent(this, ManageCorpusesActivity.class));
    }

    // call from C++ code
    public void updateProgressLabel(String newText) {
        TextView progressLabel = (TextView) findViewById(R.id.progressLabel);
        progressLabel.setText(newText);
    }

    public native float computeRecognitionRatio(String pathToSDCard, List<String> references, String hypothese);
    static {
        System.loadLibrary("native-lib");
    }


}