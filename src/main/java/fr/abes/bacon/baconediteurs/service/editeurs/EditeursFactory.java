package fr.abes.bacon.baconediteurs.service.editeurs;

public class EditeursFactory {
    public static Editeurs getEditeur(ALIAS_EDITEUR alias) {
        switch (alias) {
            case SPRINGER -> {
                return new SpringerEditeur();
            }
            case EMERALD -> {
                return new EmeraldEditeur();
            }
            default -> throw new IllegalArgumentException("Unsupported EDITEUR " + alias);
        }
    }

}
