package za.org.grassroot.graph.domain.enums;

// doing this as a final interface to use these in annotations
public interface GrassrootRelationship {

    String TYPE_GENERATOR = "GENERATOR";
    String TYPE_PARTICIPATES = "PARTICIPATES";
    String TYPE_OBSERVES = "OBSERVES";

    enum Type {
        GENERATOR,
        PARTICIPATES,
        OBSERVES
    }

    default Type ofString(String string) {
        return TYPE_GENERATOR.equals(string) ? Type.GENERATOR :
                TYPE_PARTICIPATES.equals(string) ? Type.PARTICIPATES :
                        TYPE_OBSERVES.equals(string) ? Type.OBSERVES : null;
    }

    default String ofType(Type type) {
        return Type.GENERATOR.equals(type) ? TYPE_GENERATOR :
                Type.PARTICIPATES.equals(type) ? TYPE_PARTICIPATES :
                        Type.OBSERVES.equals(type) ? TYPE_OBSERVES : null;
    }

}
