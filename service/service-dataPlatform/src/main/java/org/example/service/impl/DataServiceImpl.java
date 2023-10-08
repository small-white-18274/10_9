package org.example.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.file.service.FileStorageService;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.example.exception.CustomException;
import org.example.listener.GeneralListener;
import org.example.mapper.FileMapper;
import org.example.mapper.MonitoringDataMapper;
import org.example.mapper.PointsMapper;
import org.example.model.dto.MonitoringDataDto;
import org.example.model.entity.File;
import org.example.model.entity.MonitoringData;
import org.example.model.entity.Points;
import org.example.model.enums.AppHttpCodeEnum;
import org.example.service.DataService;
import org.example.utils.BeanCopyUtils;
import org.example.utils.FileUtil;
import org.example.utils.ResponseResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(rollbackFor = Exception.class)
public class DataServiceImpl implements DataService {

    @Resource
    private FileStorageService fileStorageService;

    @Resource
    private FileMapper fileMapper;

    @Resource
    private MonitoringDataMapper monitoringDataMapper;

    @Resource
    private PointsMapper pointsMapper;

    /**
     * 上传文件
     *
     * @param multipartFile
     * @return
     */
    @Override
    public ResponseResult uploadFile(String latitude,String longitude,MultipartFile multipartFile) {
        //检查参数
        if (multipartFile == null || multipartFile.getSize() == 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        //上传图片到minIO中
        String fileName = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        String originalFilename = multipartFile.getOriginalFilename();
        String postfix = originalFilename.substring(originalFilename.lastIndexOf("."));
        String path = null;

        //上传后得到访问路径
        try {
            path = fileStorageService.uploadFile("", fileName + postfix, multipartFile);
        } catch (Exception e) {
            e.printStackTrace();
            throw new CustomException(AppHttpCodeEnum.SERVER_ERROR);
        }

        //得到需要存放文件位置点坐标
        Integer pointId = pointsMapper.getPointId(latitude, longitude);

        if (pointId==null){
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID);
        }

        //将上传完的文件信息保存到数据库中
        File file = new File();
        file.setName(originalFilename);
        file.setPostfix(postfix);
        file.setPath(path);
        file.setType(FileUtil.getFileType(postfix.substring(1)));
        file.setTime(new Date(System.currentTimeMillis()));
        file.setPointId(pointId);

        fileMapper.insert(file);

        //返回可以访问的外链
        return ResponseResult.okResult(path);
    }

    /**
     * 通过excel得到监测数据
     *
     * @param file
     * @return
     */
    @Override
    public ResponseResult excelFile(String latitude,String longitude,MultipartFile file) {
        //判断有没有选择文件
        if (StringUtils.isBlank(file.getOriginalFilename())) {
            return ResponseResult.errorResult(AppHttpCodeEnum.UNSELECTED_FILE);
        }

        // 限制文件大小
        if (!FileUtil.checkFileSize(file.getSize(), 3, "M")) {
            throw new CustomException(AppHttpCodeEnum.OVER_SIZE);
        }

        // 限制格式
        String originalFilename = file.getOriginalFilename();
        String postfix = originalFilename.substring(originalFilename.lastIndexOf(".")).substring(1);
        if (!postfix.equals("xls") && !postfix.equals("xlsx")) {
            throw new CustomException(AppHttpCodeEnum.FILE_TYPE_ERROR);
        }

        //读取excel文件
        GeneralListener<MonitoringDataDto> generalListener=new GeneralListener<>();
        try {
            EasyExcel.read(file.getInputStream(), MonitoringDataDto.class, generalListener).sheet().doRead();
        } catch (Exception e) {
            throw new CustomException(AppHttpCodeEnum.READ_EXCEL_ERROR);
        }

        //将数据存储到数据库中
        List<MonitoringDataDto> dtoList = generalListener.getList();
        List<MonitoringData> list = BeanCopyUtils.copyBeanList(dtoList, MonitoringData.class);
        for (MonitoringData monitoringData : list) {
            monitoringDataMapper.insert(monitoringData);
        }

        return ResponseResult.okResult();
    }

    @Override            //https://ksefile.hpccube.com:65241/ui/file/#/?share=true
    public void downloadData() throws IOException {
        String url = "https://ksefile.hpccube.com:65241/efile/share/code.action?shareCode=zmhh&lockFileName=cG9zdA%3D%3D";
        CloseableHttpClient client = HttpClients.createDefault();

        // 密码校验
        HttpGet check = new HttpGet(url);
        client.execute(check);

        // 执行登录请求，获取token
        String loginUrl = "https://ksefile.hpccube.com:65241/efile/initInfo.action";
        HttpGet login = new HttpGet(loginUrl);
        CloseableHttpResponse execute = client.execute(login);
        HttpEntity entity = execute.getEntity();
        String resBody = EntityUtils.toString(entity, "UTF-8");

        //解析JSON，获取token（后面要作为下载的参数用）
        JSONObject jsonObject = JSON.parseObject(resBody);
        JSONObject dataJson = jsonObject.getJSONObject("data");
        JSONObject userInfoJson = dataJson.getJSONObject("userInfo");
        String token = userInfoJson.getString("fileTransferToken");

        // 获取当天文件的名称
        LocalDateTime dateTime = LocalDateTime.now();
        LocalDateTime yesterday = dateTime.minusHours(24);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
//        String fileName = yesterday.format(formatter);
        String fileName = "2023100412";


        //TODO 文件默认下载路径(后面再改)
        String zipPath = "D:\\YunQi_zjyj_project\\海洋气象动力图_test" + java.io.File.separator + fileName + ".zip";

        String downloadUrl = "https://ksefile.hpccube.com:65241/efile/multiDownload.action?paths=%2Fpublic%2Fhome%2Fsharedir%2Fpost%2F" + fileName + "&token=" + token;
        HttpGet download = new HttpGet(downloadUrl);
        HttpResponse response = client.execute(download);
        InputStream inputStream = response.getEntity().getContent();

        FileOutputStream fos = new FileOutputStream(zipPath);
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream();

        byte[] byt = new byte[1024];
        int ch;
        while ((ch = inputStream.read(byt)) > 0) {
            byteArray.write(byt, 0, ch);
            byteArray.flush();
        }
        fos.write(byteArray.toByteArray());
        inputStream.close();
        fos.close();
        byteArray.close();
    }
}
