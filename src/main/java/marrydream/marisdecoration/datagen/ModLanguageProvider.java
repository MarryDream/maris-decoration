package marrydream.marisdecoration.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;

import java.io.IOException;
import java.nio.file.Path;

public final class ModLanguageProvider extends FabricLanguageProvider {
    private final String languageCode;

    public ModLanguageProvider(FabricDataOutput output, String languageCode) {
        super(output, languageCode);
        this.languageCode = languageCode;
    }

    @Override
    public void generateTranslations(TranslationBuilder builder) {
        try {
            builder.add(Path.of(System.getProperty("maris.datagen.project-dir"),
                    "src", "datagen", "resources", "lang", languageCode + ".json"));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load " + languageCode + " translation source", exception);
        }
    }
}
