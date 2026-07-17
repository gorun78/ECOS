import { ApolloClient, InMemoryCache, createHttpLink } from '@apollo/client';

const graphqlClient = new ApolloClient({
  link: createHttpLink({
    uri: '/api/graphql',
  }),
  cache: new InMemoryCache(),
  defaultOptions: {
    watchQuery: {
      fetchPolicy: 'cache-and-network',
    },
    query: {
      fetchPolicy: 'network-only',
    },
  },
});

export { graphqlClient };
