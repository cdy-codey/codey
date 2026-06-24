<template>
  <el-config-provider>
    <router-view />
  </el-config-provider>
</template>

<style scoped>
:global(html),
:global(body),
:global(#app) {
  height: 100%;
  margin: 0;
  overflow: hidden;
}
</style>
