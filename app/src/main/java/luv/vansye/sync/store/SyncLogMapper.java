package luv.vansye.sync.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SyncLogMapper extends BaseMapper<SyncLogEntity> {

    @Select("SELECT * FROM sync_log WHERE delivery_id = #{deliveryId}")
    SyncLogEntity findByDeliveryId(@Param("deliveryId") String deliveryId);

    @Select("SELECT to_sha FROM sync_log WHERE status = 'success' ORDER BY id DESC LIMIT 1")
    String lastCompleteSha();

}
