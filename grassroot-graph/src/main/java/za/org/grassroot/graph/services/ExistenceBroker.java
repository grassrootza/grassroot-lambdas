package za.org.grassroot.graph.services;

public interface ExistenceBroker {

    boolean doesEntityExistInGraph(PlatformEntityDTO platformEntity);

    boolean addEntityToGraph(PlatformEntityDTO platformEntity);
}
