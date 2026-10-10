package luv.vansye.sync.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AssetMapper extends BaseMapper<AssetEntity> {

    @Select("SELECT * FROM asset WHERE content_hash = #{contentHash} AND ext = #{ext}")
    AssetEntity findByContentHashAndExt(@Param("contentHash") String contentHash, @Param("ext") String ext);

    @Update("UPDATE asset SET vault_path = #{vaultPath}, ext = #{ext}, "
            + "blob_id = #{blobId}, bytes = #{bytes}, mime = #{mime} "
            + "WHERE content_hash = #{contentHash} AND ext = #{ext}")
    int updateLocation(@Param("contentHash") String contentHash,
                       @Param("blobId") String blobId,
                       @Param("vaultPath") String vaultPath,
                       @Param("ext") String ext,
                       @Param("bytes") Integer bytes,
                       @Param("mime") String mime);
}
