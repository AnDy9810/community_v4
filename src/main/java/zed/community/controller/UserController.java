package zed.community.controller;

import zed.community.annotation.LoginRequired;
import zed.community.entity.LoginTicket;
import zed.community.entity.User;
import zed.community.service.FollowService;
import zed.community.service.LikeService;
import zed.community.service.UserService;
import zed.community.util.CommunityConstant;
import zed.community.util.CommunityUtil;
import zed.community.util.CookieUtil;
import zed.community.util.HostHolder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

@Controller
@RequestMapping("/user")
public class UserController implements CommunityConstant {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Value("${community.path.upload}")
    private String uploadPath;

    @Value("${community.path.domain}")
    private String domain;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Autowired
    private UserService userService;

    @Autowired
    private HostHolder hostHolder;

    @Autowired
    private LikeService likeService;

    @Autowired
    private FollowService followService;



    @LoginRequired
    @RequestMapping(path = "/setting",method = RequestMethod.GET)
    public String getSettingPage() {
        return "/site/setting";
    }

    @LoginRequired
    @RequestMapping(path = "/upload",method = RequestMethod.POST)
    public String uploadHeader(MultipartFile headerImage, Model model) {
        if (headerImage == null) {
            model.addAttribute("error","?????????????????????");
            return "/site/setting";
        }

        String fileName = headerImage.getOriginalFilename();
        String suffix = fileName.substring(fileName.lastIndexOf("."));
        if (StringUtils.isBlank(suffix)) {
            model.addAttribute("error", "?????????????????????");
            return "/site/setting";
        }

        //?????????????????????
        fileName = CommunityUtil.generateUUID() + suffix;
        //????????????????????????
        File dest = new File( uploadPath + "/" + fileName);
        try {
            //????????????
            headerImage.transferTo(dest);
        } catch (IOException e) {
            logger.error("??????????????????" + e.getMessage());
            throw new RuntimeException("??????????????????????????????????????????",e);
        }

        //?????????????????????????????????(web???????????????
        // http://localhost:8080/community/user/header/xxx.png
        User user = hostHolder.getUser();
        String headerUrl = domain + contextPath + "/user/header/" + fileName;
        userService.upadateHeader(user.getId(), headerUrl);

        return "redirect:/index";
    }

    @RequestMapping(path = "/header/{fileName}", method = RequestMethod.GET)
    public void getHeader(@PathVariable("fileName") String fileName, HttpServletResponse response) {
        //????????????????????????
        fileName = uploadPath + "/" +fileName;
        //????????????
        String suffix = fileName.substring(fileName.lastIndexOf("."));
        //????????????
        response.setContentType("image/" + suffix);
        try (
                //try???????????????finally????????????
                //???????????????????????????
                FileInputStream fis = new FileInputStream(fileName);
                //????????????SPRING_MVC????????????
                ServletOutputStream os = response.getOutputStream();
                ) {
            //??????????????????????????????
            byte[] buffer = new byte[1024];
            int b = 0;
            while ((b = fis.read(buffer)) != -1) {
                os.write(buffer, 0, b);
            }

        } catch (IOException e) {
            logger.error("??????????????????" + e.getMessage());
        }

    }

    @RequestMapping(path = "/keyUpdate", method = RequestMethod.POST)
    private String keyUpdate (Model model,String password, String new_password, String repeat_password, HttpServletRequest request) {
        String ticket = CookieUtil.getValue(request, "ticket");
        // ????????????
        LoginTicket loginTicket = userService.findLoginTicket(ticket);
        int id = loginTicket.getUserId();

        Map<String, Object> map = userService.updatePassword(id, password, new_password, repeat_password);

        if (map.containsKey("success")) {
            return "redirect:/index" ;
        } else {
            model.addAttribute("errorMsg1", map.get("errorMsg1"));
            model.addAttribute("errorMsg2", map.get("errorMsg2"));
            model.addAttribute("errorMsg3", map.get("errorMsg3"));
            return "/site/setting";
        }
    }

    //????????????
    @RequestMapping(path = "/profile/{userId}", method = RequestMethod.GET)
    private String getProfilePage(@PathVariable("userId") int userId, Model model) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("???????????????");
        }

        //??????
        model.addAttribute("user", user);
        //?????????
        int likeCount = likeService.findUserLikeCount(userId);
        model.addAttribute("likeCount", likeCount);

        //?????????
        long followeeCount = followService.findFolloweeCount(userId, ENTITY_TYPE_USER);
        model.addAttribute("followeeCount", followeeCount);

        //?????????
        long followerCount = followService.findFollowerCount(ENTITY_TYPE_USER, userId);
        model.addAttribute("followerCount", followerCount);

        //???????????????
        boolean hasFollowed = false;
        if (hostHolder.getUser() != null) {
            hasFollowed = followService.hasFollowed(hostHolder.getUser().getId(), ENTITY_TYPE_USER, userId);
        };
        model.addAttribute("hasFollowed", hasFollowed);

        return "/site/profile";


    }



}
