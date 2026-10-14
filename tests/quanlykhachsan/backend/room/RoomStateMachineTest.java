package quanlykhachsan.backend.room;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class RoomStateMachineTest {
    @ParameterizedTest @CsvSource({
        "vacant_clean,occupied_clean,front_desk,true","reserved,occupied_clean,front_desk,true",
        "occupied_clean,vacant_dirty,front_desk,true","occupied_dirty,vacant_dirty,front_desk,true",
        "vacant_dirty,vacant_clean,housekeeping,true","occupied_dirty,occupied_clean,housekeeping,true",
        "vacant_clean,out_of_order,maintenance,true","reserved,out_of_order,maintenance,true",
        "occupied_clean,out_of_order,maintenance,true","vacant_dirty,out_of_order,maintenance,true",
        "out_of_order,vacant_clean,maintenance,true","out_of_service,vacant_clean,maintenance,true",
        "vacant_clean,out_of_service,maintenance,true","vacant_dirty,vacant_clean,admin,true",
        "vacant_dirty,vacant_clean,front_desk,false","occupied_clean,vacant_clean,front_desk,false",
        "occupied_dirty,vacant_clean,housekeeping,false","occupied_clean,vacant_clean,admin,false",
        "vacant_clean,occupied_clean,housekeeping,false","vacant_clean,out_of_order,front_desk,false",
        "occupied_clean,vacant_dirty,f&b,false","vacant_clean,occupied_clean,guest,false",
        "vacant_clean,reserved,housekeeping,false","vacant_clean,vacant_clean,housekeeping,false",
        "reserved,vacant_clean,front_desk,false"})
    void rulesSeparateResponsibilitiesAndForbidSkippedCleaning(String from,String to,String role,boolean allowed) {
        var machine=new RoomStateMachine();if(allowed)assertEquals(to,machine.transition(from,to,role));else assertThrows(RuntimeException.class,()->machine.transition(from,to,role));
    }
}
