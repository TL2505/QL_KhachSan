package quanlykhachsan.backend.room;

import java.util.Set;

public final class RoomStateMachine {
    private static final Set<String> STATES=Set.of("vacant_clean","vacant_dirty","occupied_clean","occupied_dirty","reserved","out_of_order","out_of_service");
    public String transition(String from,String to,String role) {
        if(!STATES.contains(from) || !STATES.contains(to))throw new IllegalArgumentException("Invalid room state");
        if(!Set.of("admin","front_desk","housekeeping","maintenance").contains(role))throw new SecurityException("Role is not authorized");
        if(from.startsWith("occupied_") && to.equals("vacant_clean"))throw new RoomStateTransitionException();
        boolean desk=(from.equals("vacant_clean") || from.equals("reserved")) && to.equals("occupied_clean")
            || from.startsWith("occupied_") && to.equals("vacant_dirty");
        boolean clean=from.equals("vacant_dirty") && to.equals("vacant_clean") || from.equals("occupied_dirty") && to.equals("occupied_clean");
        boolean repair=Set.of("out_of_order","out_of_service").contains(to) && !from.equals(to)
            || Set.of("out_of_order","out_of_service").contains(from) && to.equals("vacant_clean");
        if(role.equals("front_desk") && clean)throw new SecurityException("Cleaning requires a housekeeping role");
        if((role.equals("admin") && (desk || clean || repair)) || role.equals("front_desk") && desk
            || role.equals("housekeeping") && clean || role.equals("maintenance") && repair)return to;
        if((clean && !role.equals("housekeeping")) || (repair && !role.equals("maintenance")) || (desk && !role.equals("front_desk")))throw new SecurityException("Role is not authorized");
        throw new RoomStateTransitionException();
    }
}
