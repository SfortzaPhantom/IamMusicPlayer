package red.felnull.imp.client.data;

import com.mpatric.mp3agic.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import red.felnull.imp.client.gui.toasts.MusicUploadToast;
import red.felnull.imp.util.PathUtil;
import red.felnull.otyacraftengine.util.IKSGFileLoadUtil;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.info.MultimediaInfo;
import ws.schild.jave.progress.EncoderProgressListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

@OnlyIn(Dist.CLIENT)
public class MusicUploader {
    private static MusicUploader INSTANCE;
    private final Map<String, MusicUploadData> stateDatas = new HashMap<>();

    public static void init() {
        INSTANCE = new MusicUploader();
    }

    public static MusicUploader instance() {
        return INSTANCE;
    }

    public void startUpload(Path path, String uuid) {
        stateDatas.put(uuid, new MusicUploadData());
        MusicUploadToast.add(uuid);
        UploadThread ut = new UploadThread(path, uuid);
        ut.start();
    }

    protected void upload(Path path, String uuid) {
        try {
            setState(uuid, MusicUploadData.UploadState.CONVERTING);
            conversion(path, uuid, 128);
            setState(uuid, MusicUploadData.UploadState.COMPRESSING);
            byte[] compdata = compressing(uuid);
            setState(uuid, MusicUploadData.UploadState.COMPLETION);
            IKSGFileLoadUtil.fileBytesWriter(compdata, PathUtil.getTmpFolder().resolve(uuid + ".gz"));

        } catch (Exception ex) {
            ex.printStackTrace();
            setState(uuid, MusicUploadData.UploadState.ERROR);
        }
    }

    public boolean isUploaded(String uuid) {
        return stateDatas.containsKey(uuid);
    }

    protected void setState(String uuid, MusicUploadData.UploadState state) {
        stateDatas.get(uuid).setState(state);
    }

    protected void setProgress(String uuid, float parsent) {
        stateDatas.get(uuid).setProgress(parsent);
    }

    public MusicUploadData getStateData(String uuid) {
        return stateDatas.get(uuid);
    }

    private byte[] compressing(String uuid) throws IOException {
        File file = PathUtil.getTmpFolder().resolve(uuid).toFile();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] bytes = IKSGFileLoadUtil.fileBytesReader(file.toPath());
        GZIPOutputStream gzip_out = new GZIPOutputStream(out);
        gzip_out.write(bytes);
        gzip_out.close();
        out.close();
        byte[] ret = out.toByteArray();
        IKSGFileLoadUtil.deleteFile(file);
        return ret;
    }


    private void conversion(Path path, String uuid, int bitrate) throws EncoderException, InvalidDataException, IOException, UnsupportedTagException, NotSupportedException {
        IKSGFileLoadUtil.createFolder(PathUtil.getTmpFolder());
        File mf = path.toFile();
        MultimediaObject mo = new MultimediaObject(mf);
        AudioAttributes aa = new AudioAttributes();
        aa.setCodec("libmp3lame");
        aa.setBitRate(bitrate);
        aa.setChannels(2);
        aa.setSamplingRate(32000);
        EncodingAttributes ea = new EncodingAttributes();
        ea.setOutputFormat("mp3");
        ea.setAudioAttributes(aa);
        Encoder encoder = new Encoder();
        encoder.encode(mo, PathUtil.getTmpFolder().resolve(uuid + "-tmp").toFile(), ea, new EncoderProgressListener() {
            @Override
            public void sourceInfo(MultimediaInfo info) {
            }

            @Override
            public void progress(int permil) {
                setProgress(uuid, (float) permil / 1000f);
            }

            @Override
            public void message(String message) {
            }
        });
        Mp3File m3f = new Mp3File(PathUtil.getTmpFolder().resolve(uuid + "-tmp").toFile());
        m3f.setId3v1Tag(new ID3v1Tag());
        m3f.setId3v2Tag(new ID3v24Tag());
        m3f.setCustomTag(new byte[0]);
        m3f.save(PathUtil.getTmpFolder().resolve(uuid).toString());
        IKSGFileLoadUtil.deleteFile(PathUtil.getTmpFolder().resolve(uuid + "-tmp"));
    }

    private class UploadThread extends Thread {
        private final Path path;
        private final String uuid;

        public UploadThread(Path path, String uuid) {
            this.path = path;
            this.uuid = uuid;
        }

        @Override
        public void run() {
            upload(path, uuid);
        }
    }


}
