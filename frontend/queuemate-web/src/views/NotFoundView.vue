<script setup>
import StatePanel from '../components/StatePanel.vue'
</script>

<template>
  <StatePanel title="找不到这个页面" description="地址可能有误，或页面已经移动。">
    <RouterLink to="/venues"><el-button type="primary">返回地点列表</el-button></RouterLink>
  </StatePanel>
</template>
