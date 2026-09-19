export default {
  name: "research-promise-return-plugin",
  version: "0.0.0",
  apiVersion: 1,
  renderers: {
    column() {
      return Promise.resolve("<aside>async</aside>");
    },
  },
};
