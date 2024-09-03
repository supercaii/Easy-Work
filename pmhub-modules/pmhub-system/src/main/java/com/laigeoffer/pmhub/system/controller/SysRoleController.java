package com.laigeoffer.pmhub.system.controller;

import cn.hutool.core.util.ObjectUtil;
import com.laigeoffer.pmhub.base.core.annotation.Log;
import com.laigeoffer.pmhub.base.core.constant.UserConstants;
import com.laigeoffer.pmhub.base.core.core.controller.BaseController;
import com.laigeoffer.pmhub.base.core.core.domain.AjaxResult;
import com.laigeoffer.pmhub.base.core.core.domain.entity.SysDept;
import com.laigeoffer.pmhub.base.core.core.domain.entity.SysRole;
import com.laigeoffer.pmhub.base.core.core.domain.entity.SysUser;
import com.laigeoffer.pmhub.base.core.core.domain.model.LoginUser;
import com.laigeoffer.pmhub.base.core.core.page.TableDataInfo;
import com.laigeoffer.pmhub.base.core.enums.BusinessType;
import com.laigeoffer.pmhub.base.core.utils.StringUtils;
import com.laigeoffer.pmhub.base.core.utils.poi.ExcelUtil;
import com.laigeoffer.pmhub.base.security.annotation.RequiresPermissions;
import com.laigeoffer.pmhub.base.security.service.TokenService;
import com.laigeoffer.pmhub.base.security.utils.SecurityUtils;
import com.laigeoffer.pmhub.system.domain.SysUserRole;
import com.laigeoffer.pmhub.system.service.ISysDeptService;
import com.laigeoffer.pmhub.system.service.ISysRoleService;
import com.laigeoffer.pmhub.system.service.ISysUserService;
import com.laigeoffer.pmhub.system.service.impl.SysPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * 角色信息
 *
 * @author canghe
 */
@RestController
@RequestMapping("/system/role")
public class SysRoleController extends BaseController {
    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private TokenService tokenService;

    // TODO: 2024.04.24
//    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysDeptService deptService;



    @RequiresPermissions("system:role:list")
    @GetMapping("/list")
    public TableDataInfo list(SysRole role) {
        startPage();
        List<SysRole> list = roleService.selectRoleList(role);
        return getDataTable(list);
    }

    @Log(title = "角色管理", businessType = BusinessType.EXPORT)
    @RequiresPermissions("system:role:export")
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysRole role) {
        List<SysRole> list = roleService.selectRoleList(role);
        ExcelUtil<SysRole> util = new ExcelUtil<SysRole>(SysRole.class);
        util.exportExcel(response, list, "角色数据");
    }

    /**
     * 根据角色编号获取详细信息
     */
    @RequiresPermissions("system:role:query")
    @GetMapping(value = "/{roleId}")
    public AjaxResult getInfo(@PathVariable Long roleId) {
        roleService.checkRoleDataScope(roleId);
        return success(roleService.selectRoleById(roleId));
    }

    /**
     * 新增角色
     */
    @RequiresPermissions("system:role:add")
    @Log(title = "角色管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysRole role) {
        if (UserConstants.NOT_UNIQUE.equals(roleService.checkRoleNameUnique(role))) {
            return error("新增角色'" + role.getRoleName() + "'失败，角色名称已存在");
        } else if (UserConstants.NOT_UNIQUE.equals(roleService.checkRoleKeyUnique(role))) {
            return error("新增角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }
        role.setCreateBy(SecurityUtils.getUsername());
        return toAjax(roleService.insertRole(role));

    }

    /**
     * 修改保存角色
     */
    @RequiresPermissions("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysRole role) {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        if (UserConstants.NOT_UNIQUE.equals(roleService.checkRoleNameUnique(role))) {
            return error("修改角色'" + role.getRoleName() + "'失败，角色名称已存在");
        } else if (UserConstants.NOT_UNIQUE.equals(roleService.checkRoleKeyUnique(role))) {
            return error("修改角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }
        role.setUpdateBy(SecurityUtils.getUsername());

        if (roleService.updateRole(role) > 0) {
            // 更新缓存用户权限
            LoginUser loginUser = SecurityUtils.getLoginUser();
            if (StringUtils.isNotNull(loginUser.getUser()) && !loginUser.getUser().isAdmin()) {
                // TODO: 2024.04.24
//                loginUser.setPermissions(permissionService.getMenuPermission(loginUser.getUser()));
                loginUser.setUser(userService.selectUserByUserName(loginUser.getUser().getUserName()));
                tokenService.setLoginUser(loginUser);
            }
            // 角色相关的用户全部刷新权限
            SysUser user = new SysUser();
            user.setRoleId(role.getRoleId());
            List<SysUser> list = userService.selectAllocatedList(user);
            for (SysUser newUser:list){
                // TODO: 2024.04.24
//                tokenService.updateToken(new LoginUser(newUser.getUserId()
//                        , newUser.getDeptId()
//                        , newUser
//                        , permissionService.getMenuPermission(newUser)));
            }
            return success();
        }
        return error("修改角色'" + role.getRoleName() + "'失败，请联系管理员");
    }

    /**
     * 修改保存数据权限
     */
    @RequiresPermissions("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping("/dataScope")
    public AjaxResult dataScope(@RequestBody SysRole role) {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        // 角色相关的用户
        SysUser user = new SysUser();
        user.setRoleId(role.getRoleId());
        List<SysUser> list = userService.selectAllocatedList(user);
        // 修改角色
        int count  = roleService.authDataScope(role);
        // 更新用户信息
        if (count>0){
            for (SysUser newUser:list){
                // 全部刷新权限
                // TODO: 2024.04.24  
//                tokenService.updateToken(new LoginUser(newUser.getUserId()
//                        , newUser.getDeptId()
//                        , newUser
//                        , permissionService.getMenuPermission(newUser)));
            }
        }
        return toAjax(count);
    }

    /**
     * 状态修改
     */
    @RequiresPermissions("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SysRole role) {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        role.setUpdateBy(SecurityUtils.getUsername());
        // 角色相关的用户
        SysUser user = new SysUser();
        user.setRoleId(role.getRoleId());
        List<SysUser> list = userService.selectAllocatedList(user);
        // 修改角色
        int count  = roleService.updateRoleStatus(role);
        // 更新用户信息
        if (count>0){
            for (SysUser newUser:list){
                // TODO: 2024.04.24
//                // 全部刷新权限
//                tokenService.updateToken(new LoginUser(newUser.getUserId()
//                        , newUser.getDeptId()
//                        , newUser
//                        , permissionService.getMenuPermission(newUser)));
            }
        }
        return toAjax(count);
    }

    /**
     * 删除角色
     */
    @RequiresPermissions("system:role:remove")
    @Log(title = "角色管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{roleIds}")
    public AjaxResult remove(@PathVariable Long[] roleIds) {
        List<SysUser> list = new ArrayList<>();
        for (Long roleId:roleIds){
            // 角色相关的用户
            SysUser user = new SysUser();
            user.setRoleId(roleId);
            list.addAll(userService.selectAllocatedList(user));
        }
        // 删除角色
        int count = roleService.deleteRoleByIds(roleIds);
        if (count>0){
            for (SysUser newUser:list){
                // 全部刷新权限
                // TODO: 2024.04.24  
//                tokenService.updateToken(new LoginUser(newUser.getUserId()
//                        , newUser.getDeptId()
//                        , newUser
//                        , permissionService.getMenuPermission(newUser)));
            }
        }
        return toAjax(count);
    }

    /**
     * 获取角色选择框列表
     */
    @RequiresPermissions("system:role:query")
    @GetMapping("/optionselect")
    public AjaxResult optionselect() {
        return success(roleService.selectRoleAll());
    }

    /**
     * 查询已分配用户角色列表
     */
    @RequiresPermissions("system:role:list")
    @GetMapping("/authUser/allocatedList")
    public TableDataInfo allocatedList(SysUser user) {
        startPage();
        List<SysUser> list = userService.selectAllocatedList(user);
        return getDataTable(list);
    }

    /**
     * 查询未分配用户角色列表
     */
    @RequiresPermissions("system:role:list")
    @GetMapping("/authUser/unallocatedList")
    public TableDataInfo unallocatedList(SysUser user) {
        startPage();
        List<SysUser> list = userService.selectUnallocatedList(user);
        return getDataTable(list);
    }

    /**
     * 取消授权用户
     */
    @RequiresPermissions("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/authUser/cancel")
    public AjaxResult cancelAuthUser(@RequestBody SysUserRole userRole) {
        int count = roleService.deleteAuthUser(userRole);
        // 如果完成用户修改，就刷新用户缓存
        if (count>0){
            SysUser newUser = userService.selectUserById(userRole.getUserId());
            if (ObjectUtil.isNotEmpty(newUser)){
                // TODO: 2024.04.24  
//                tokenService.updateToken(new LoginUser(newUser.getUserId()
//                        , newUser.getDeptId()
//                        , newUser
//                        , permissionService.getMenuPermission(newUser)));
            }
        }
        return toAjax(count);
    }

    /**
     * 批量取消授权用户
     */
    @RequiresPermissions("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/authUser/cancelAll")
    public AjaxResult cancelAuthUserAll(Long roleId, Long[] userIds) {
        int count = roleService.deleteAuthUsers(roleId, userIds);
        // 如果完成用户修改，就刷新用户缓存
        if (count>0){
            for (Long userId:userIds){
                SysUser newUser = userService.selectUserById(userId);
                if (ObjectUtil.isNotEmpty(newUser)){
                    // TODO: 2024.04.24  
//                    tokenService.updateToken(new LoginUser(newUser.getUserId()
//                            , newUser.getDeptId()
//                            , newUser
//                            , permissionService.getMenuPermission(newUser)));
                }
            }
        }
        return toAjax(count);
    }

    /**
     * 批量选择用户授权
     */
    @RequiresPermissions("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/authUser/selectAll")
    public AjaxResult selectAuthUserAll(Long roleId, Long[] userIds) {
        roleService.checkRoleDataScope(roleId);
        int count = roleService.insertAuthUsers(roleId, userIds);
        // 如果完成用户修改，就刷新用户缓存
        if (count>0){
            for (Long userId:userIds){
                SysUser newUser = userService.selectUserById(userId);
                if (ObjectUtil.isNotEmpty(newUser)){
                    // TODO: 2024.04.24
//                    tokenService.updateToken(new LoginUser(newUser.getUserId()
//                            , newUser.getDeptId()
//                            , newUser
//                            , permissionService.getMenuPermission(newUser)));
                }
            }
        }
        return toAjax(count);
    }

    /**
     * 获取对应角色部门树列表
     */
    @RequiresPermissions("system:role:query")
    @GetMapping(value = "/deptTree/{roleId}")
    public AjaxResult deptTree(@PathVariable("roleId") Long roleId) {
        AjaxResult ajax = AjaxResult.success();
        ajax.put("checkedKeys", deptService.selectDeptListByRoleId(roleId));
        ajax.put("depts", deptService.selectDeptTreeList(new SysDept()));
        return ajax;
    }
}